package com.cc4c.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
/** 协调可靠消息的解密、幂等、业务处理、重试和死信确认流程。 */
/** ReliableMessageProcessor 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class ReliableMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReliableMessageProcessor.class);

    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final MessagePayloadCipher cipher;
    private final ObjectMapper objectMapper;
    private final RabbitMessagePublisher publisher;
    private final MessagingTopology topology;
    private final Cc4cMetrics metrics;
    private final ReliableMessageProtocolSupport protocolSupport;
    private final String workerId = "consumer-" + UUID.randomUUID();

    @Autowired
    public ReliableMessageProcessor(
            InboxRepository inbox,
            OutboxRepository outbox,
            MessagePayloadCipher cipher,
            ObjectMapper objectMapper,
            RabbitMessagePublisher publisher,
            MessagingTopology topology,
            Cc4cMetrics metrics) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.topology = topology;
        this.metrics = metrics;
        this.protocolSupport = new ReliableMessageProtocolSupport();
    }

    public ReliableMessageProcessor(
            InboxRepository inbox,
            OutboxRepository outbox,
            MessagePayloadCipher cipher,
            ObjectMapper objectMapper,
            RabbitMessagePublisher publisher,
            MessagingTopology topology) {
        this(inbox, outbox, cipher, objectMapper, publisher, topology, Cc4cMetrics.disabled());
    }

    public void process(
            String consumerName,
            String expectedEventType,
            Message message,
            Channel channel,
            long deliveryTag,
            ReliableMessageHandler handler)
            throws IOException {
        String fallback = correlationFallback(message);
        Object header = message.getMessageProperties().getHeaders().get(CorrelationIds.AMQP_HEADER);
        String correlationId = CorrelationIds.normalize(header instanceof String value ? value : null, fallback);
        long startedNanos = metrics.start();
        try (CorrelationIds.Scope ignored = CorrelationIds.open(correlationId)) {
            processCorrelated(consumerName, expectedEventType, message, channel, deliveryTag, handler);
            metrics.record(
                    "cc4c.messaging.consume.duration",
                    startedNanos,
                    "event_type",
                    expectedEventType,
                    "outcome",
                    "completed");
        } catch (IOException | RuntimeException exception) {
            metrics.record(
                    "cc4c.messaging.consume.duration",
                    startedNanos,
                    "event_type",
                    expectedEventType,
                    "outcome",
                    "error");
            throw exception;
        }
    }

    private String correlationFallback(Message message) {
        Optional<String> reference =
                protocolSupport.messageReference(message).map(ReliableMessageProtocolSupport.MessageReference::eventId);
        if (reference.isPresent()) {
            return reference.get();
        }
        if (message.getBody().length > MessagePayloadCipher.MAX_PLAINTEXT_BYTES * 2) {
            return null;
        }
        try {
            return objectMapper
                    .readValue(message.getBody(), MessageEnvelope.class)
                    .eventId();
        } catch (RuntimeException | IOException exception) {
            return null;
        }
    }

    private void processCorrelated(
            String consumerName,
            String expectedEventType,
            Message message,
            Channel channel,
            long deliveryTag,
            ReliableMessageHandler handler)
            throws IOException {
        MessageEnvelope envelope;
        try {
            if (message.getBody().length > MessagePayloadCipher.MAX_PLAINTEXT_BYTES * 2) {
                throw new MessagePayloadException("ENVELOPE_TOO_LARGE", "Message envelope is too large");
            }
            envelope = objectMapper.readValue(message.getBody(), MessageEnvelope.class);
        } catch (RuntimeException | IOException exception) {
            log.atWarn()
                    .addKeyValue("event", "message_consume")
                    .addKeyValue("event_type", expectedEventType)
                    .addKeyValue("result", "invalid_envelope")
                    .log("Message envelope was rejected");
            rejectMalformedEnvelope(
                    consumerName,
                    expectedEventType,
                    message,
                    channel,
                    deliveryTag,
                    exception instanceof MessagePayloadException payloadException
                            ? payloadException.errorCode()
                            : "INVALID_ENVELOPE");
            return;
        }
        try {
            protocolSupport.validateEnvelope(envelope, expectedEventType);
        } catch (MessagePayloadException exception) {
            deadWithoutHandler(consumerName, envelope, message, channel, deliveryTag, exception.errorCode());
            return;
        }

        byte[] plaintext;
        try {
            plaintext = cipher.decrypt(envelope);
        } catch (MessagePayloadException exception) {
            deadWithoutHandler(consumerName, envelope, message, channel, deliveryTag, exception.errorCode());
            return;
        }

        InboxClaim claim = inbox.claim(
                consumerName,
                envelope.eventId(),
                envelope.generation(),
                workerId,
                Instant.now().plusSeconds(300));
        if (claim != InboxClaim.ACQUIRED) {
            if (claim == InboxClaim.ALREADY_DONE) {
                metrics.increment("cc4c.messaging.duplicates", "event_type", envelope.eventType());
            }
            channel.basicAck(deliveryTag, false);
            return;
        }
        outbox.incrementConsumeAttempt(envelope.eventId(), envelope.generation());

        if (envelope.expiresAt() != null && !envelope.expiresAt().isAfter(Instant.now())) {
            try {
                handler.expired(envelope, plaintext);
                inbox.markDone(consumerName, envelope.eventId(), envelope.generation());
                outbox.markExpired(envelope.eventId(), envelope.generation());
                metrics.increment("cc4c.messaging.expired", "event_type", envelope.eventType());
                channel.basicAck(deliveryTag, false);
            } catch (MessagePayloadException exception) {
                handleFailure(
                        consumerName,
                        envelope,
                        plaintext,
                        message,
                        channel,
                        deliveryTag,
                        handler,
                        exception.errorCode(),
                        true);
            }
            return;
        }

        try {
            handler.handle(envelope, plaintext);
            inbox.markDone(consumerName, envelope.eventId(), envelope.generation());
            outbox.markDelivered(envelope.eventId(), envelope.generation());
            channel.basicAck(deliveryTag, false);
            log.atInfo()
                    .addKeyValue("event", "message_consume")
                    .addKeyValue("event_id", envelope.eventId())
                    .addKeyValue("event_type", envelope.eventType())
                    .addKeyValue("generation", envelope.generation())
                    .addKeyValue("result", "delivered")
                    .log("Message consumption completed");
        } catch (MailDeliveryException exception) {
            handleFailure(
                    consumerName,
                    envelope,
                    plaintext,
                    message,
                    channel,
                    deliveryTag,
                    handler,
                    exception.errorCode(),
                    exception.permanent());
        } catch (MessagePayloadException exception) {
            handleFailure(
                    consumerName,
                    envelope,
                    plaintext,
                    message,
                    channel,
                    deliveryTag,
                    handler,
                    exception.errorCode(),
                    true);
        } catch (RuntimeException exception) {
            handleFailure(
                    consumerName,
                    envelope,
                    plaintext,
                    message,
                    channel,
                    deliveryTag,
                    handler,
                    "UNKNOWN_EXHAUSTED",
                    false);
        }
    }

    private void handleFailure(
            String consumerName,
            MessageEnvelope envelope,
            byte[] plaintext,
            Message original,
            Channel channel,
            long deliveryTag,
            ReliableMessageHandler handler,
            String errorCode,
            boolean permanent)
            throws IOException {
        int attempt =
                protocolSupport.retryAttempt(original, topology.retryDelays().size());
        if (!permanent && attempt < topology.retryDelays().size()) {
            inbox.markRetryWaiting(consumerName, envelope.eventId(), envelope.generation(), errorCode);
            Message retry = protocolSupport.copyForRetry(original, envelope, attempt + 1);
            PublishOutcome outcome = publisher.publish(
                    "",
                    topology.retryQueue(envelope.eventType(), attempt),
                    retry,
                    envelope.eventId() + ":retry:" + (attempt + 1));
            if (outcome.accepted()) {
                metrics.increment("cc4c.messaging.retries", "event_type", envelope.eventType(), "stage", "consumer");
                channel.basicAck(deliveryTag, false);
                log.atWarn()
                        .addKeyValue("event", "message_consume")
                        .addKeyValue("event_id", envelope.eventId())
                        .addKeyValue("event_type", envelope.eventType())
                        .addKeyValue("generation", envelope.generation())
                        .addKeyValue("attempt", attempt + 1)
                        .addKeyValue("result", "retry_scheduled")
                        .log("Message retry was scheduled");
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
            return;
        }

        inbox.markDead(consumerName, envelope.eventId(), envelope.generation(), errorCode);
        outbox.markDead(envelope.eventId(), envelope.generation(), errorCode);
        handler.dead(envelope, plaintext, errorCode);
        Message dead = protocolSupport.copyForDead(original, envelope, errorCode);
        PublishOutcome outcome = publisher.publish(
                topology.deadExchange(),
                envelope.eventType() + ".dead",
                dead,
                envelope.eventId() + ":dead:" + envelope.generation());
        if (outcome.accepted()) {
            metrics.increment("cc4c.messaging.dead", "event_type", envelope.eventType(), "error_code", errorCode);
            channel.basicAck(deliveryTag, false);
            log.atWarn()
                    .addKeyValue("event", "message_consume")
                    .addKeyValue("event_id", envelope.eventId())
                    .addKeyValue("event_type", envelope.eventType())
                    .addKeyValue("generation", envelope.generation())
                    .addKeyValue("result", "dead")
                    .addKeyValue("error_code", errorCode)
                    .log("Message entered the dead-letter path");
        } else {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void deadWithoutHandler(
            String consumerName,
            MessageEnvelope envelope,
            Message original,
            Channel channel,
            long deliveryTag,
            String errorCode)
            throws IOException {
        InboxClaim claim = inbox.claim(
                consumerName,
                envelope.eventId(),
                envelope.generation(),
                workerId,
                Instant.now().plusSeconds(30));
        if (claim != InboxClaim.ACQUIRED) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        inbox.markDead(consumerName, envelope.eventId(), envelope.generation(), errorCode);
        outbox.markDead(envelope.eventId(), envelope.generation(), errorCode);
        PublishOutcome outcome = publisher.publish(
                topology.deadExchange(),
                envelope.eventType() + ".dead",
                protocolSupport.copyForDead(original, envelope, errorCode),
                envelope.eventId() + ":dead:" + envelope.generation());
        if (outcome.accepted()) {
            metrics.increment("cc4c.messaging.dead", "event_type", envelope.eventType(), "error_code", errorCode);
            channel.basicAck(deliveryTag, false);
        } else {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void rejectMalformedEnvelope(
            String consumerName,
            String expectedEventType,
            Message original,
            Channel channel,
            long deliveryTag,
            String errorCode)
            throws IOException {
        protocolSupport.messageReference(original).ifPresent(reference -> {
            InboxClaim claim = inbox.claim(
                    consumerName,
                    reference.eventId(),
                    reference.generation(),
                    workerId,
                    Instant.now().plusSeconds(30));
            if (claim == InboxClaim.ACQUIRED) {
                inbox.markDead(consumerName, reference.eventId(), reference.generation(), errorCode);
                outbox.markDead(reference.eventId(), reference.generation(), errorCode);
            }
        });
        log.atWarn()
                .addKeyValue("event", "message_consume")
                .addKeyValue("event_type", expectedEventType)
                .addKeyValue("result", "dead")
                .addKeyValue("error_code", errorCode)
                .log("Malformed message entered the dead-letter path");
        // Quorum at-least-once dead lettering confirms the transfer before removing the source message.
        channel.basicReject(deliveryTag, false);
    }
}
