package com.cc4c.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public final class ReliableMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReliableMessageProcessor.class);
    private static final String RETRY_HEADER = "cc4c-retry-attempt";

    private final InboxRepository inbox;
    private final OutboxRepository outbox;
    private final MessagePayloadCipher cipher;
    private final ObjectMapper objectMapper;
    private final RabbitMessagePublisher publisher;
    private final MessagingTopology topology;
    private final String workerId = "consumer-" + UUID.randomUUID();

    public ReliableMessageProcessor(
            InboxRepository inbox,
            OutboxRepository outbox,
            MessagePayloadCipher cipher,
            ObjectMapper objectMapper,
            RabbitMessagePublisher publisher,
            MessagingTopology topology) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.topology = topology;
    }

    public void process(
            String consumerName,
            String expectedEventType,
            Message message,
            Channel channel,
            long deliveryTag,
            ReliableMessageHandler handler) throws IOException {
        MessageEnvelope envelope;
        try {
            if (message.getBody().length > MessagePayloadCipher.MAX_PLAINTEXT_BYTES * 2) {
                throw new MessagePayloadException("ENVELOPE_TOO_LARGE", "Message envelope is too large");
            }
            envelope = objectMapper.readValue(message.getBody(), MessageEnvelope.class);
        } catch (RuntimeException | IOException exception) {
            log.warn("messaging_action=consume type={} result=invalid_envelope", expectedEventType);
            rejectMalformedEnvelope(
                    consumerName, expectedEventType, message, channel, deliveryTag,
                    exception instanceof MessagePayloadException payloadException
                            ? payloadException.errorCode()
                            : "INVALID_ENVELOPE");
            return;
        }
        try {
            validateEnvelope(envelope, expectedEventType);
        } catch (MessagePayloadException exception) {
            deadWithoutHandler(
                    consumerName, envelope, message, channel, deliveryTag, exception.errorCode());
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
                consumerName, envelope.eventId(), envelope.generation(), workerId,
                Instant.now().plusSeconds(300));
        if (claim != InboxClaim.ACQUIRED) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        outbox.incrementConsumeAttempt(envelope.eventId(), envelope.generation());

        if (envelope.expiresAt() != null && !envelope.expiresAt().isAfter(Instant.now())) {
            try {
                handler.expired(envelope, plaintext);
                inbox.markDone(consumerName, envelope.eventId(), envelope.generation());
                outbox.markExpired(envelope.eventId(), envelope.generation());
                channel.basicAck(deliveryTag, false);
            } catch (MessagePayloadException exception) {
                handleFailure(
                        consumerName, envelope, plaintext, message, channel, deliveryTag, handler,
                        exception.errorCode(), true);
            }
            return;
        }

        try {
            handler.handle(envelope, plaintext);
            inbox.markDone(consumerName, envelope.eventId(), envelope.generation());
            outbox.markDelivered(envelope.eventId(), envelope.generation());
            channel.basicAck(deliveryTag, false);
            log.info(
                    "messaging_action=consume event={} type={} generation={} result=delivered",
                    envelope.eventId(), envelope.eventType(), envelope.generation());
        } catch (MailDeliveryException exception) {
            handleFailure(
                    consumerName, envelope, plaintext, message, channel, deliveryTag, handler,
                    exception.errorCode(), exception.permanent());
        } catch (MessagePayloadException exception) {
            handleFailure(
                    consumerName, envelope, plaintext, message, channel, deliveryTag, handler,
                    exception.errorCode(), true);
        } catch (RuntimeException exception) {
            handleFailure(
                    consumerName, envelope, plaintext, message, channel, deliveryTag, handler,
                    "UNKNOWN_EXHAUSTED", false);
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
            boolean permanent) throws IOException {
        int attempt = retryAttempt(original);
        if (!permanent && attempt < topology.retryDelays().size()) {
            inbox.markRetryWaiting(consumerName, envelope.eventId(), envelope.generation(), errorCode);
            Message retry = copyForRetry(original, envelope, attempt + 1);
            PublishOutcome outcome = publisher.publish(
                    "", topology.retryQueue(envelope.eventType(), attempt), retry,
                    envelope.eventId() + ":retry:" + (attempt + 1));
            if (outcome.accepted()) {
                channel.basicAck(deliveryTag, false);
                log.warn(
                        "messaging_action=consume event={} type={} generation={} attempt={} result=retry_scheduled",
                        envelope.eventId(), envelope.eventType(), envelope.generation(), attempt + 1);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
            return;
        }

        inbox.markDead(consumerName, envelope.eventId(), envelope.generation(), errorCode);
        outbox.markDead(envelope.eventId(), envelope.generation(), errorCode);
        handler.dead(envelope, plaintext, errorCode);
        Message dead = copyForDead(original, envelope, errorCode);
        PublishOutcome outcome = publisher.publish(
                topology.deadExchange(), envelope.eventType() + ".dead", dead,
                envelope.eventId() + ":dead:" + envelope.generation());
        if (outcome.accepted()) {
            channel.basicAck(deliveryTag, false);
            log.warn(
                    "messaging_action=consume event={} type={} generation={} result=dead error={}",
                    envelope.eventId(), envelope.eventType(), envelope.generation(), errorCode);
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
            String errorCode) throws IOException {
        InboxClaim claim = inbox.claim(
                consumerName, envelope.eventId(), envelope.generation(), workerId,
                Instant.now().plusSeconds(30));
        if (claim != InboxClaim.ACQUIRED) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        inbox.markDead(consumerName, envelope.eventId(), envelope.generation(), errorCode);
        outbox.markDead(envelope.eventId(), envelope.generation(), errorCode);
        PublishOutcome outcome = publisher.publish(
                topology.deadExchange(), envelope.eventType() + ".dead",
                copyForDead(original, envelope, errorCode),
                envelope.eventId() + ":dead:" + envelope.generation());
        if (outcome.accepted()) {
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
            String errorCode) throws IOException {
        messageReference(original).ifPresent(reference -> {
            InboxClaim claim = inbox.claim(
                    consumerName, reference.eventId(), reference.generation(), workerId,
                    Instant.now().plusSeconds(30));
            if (claim == InboxClaim.ACQUIRED) {
                inbox.markDead(consumerName, reference.eventId(), reference.generation(), errorCode);
                outbox.markDead(reference.eventId(), reference.generation(), errorCode);
            }
        });
        log.warn(
                "messaging_action=consume type={} result=dead error={}",
                expectedEventType, errorCode);
        // Quorum at-least-once dead lettering confirms the transfer before removing the source message.
        channel.basicReject(deliveryTag, false);
    }

    private Optional<MessageReference> messageReference(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            return Optional.empty();
        }
        int separator = messageId.lastIndexOf(':');
        if (separator <= 0 || separator == messageId.length() - 1) {
            return Optional.empty();
        }
        try {
            String eventId = messageId.substring(0, separator);
            UUID parsed = UUID.fromString(eventId);
            int generation = Integer.parseInt(messageId.substring(separator + 1));
            if (!parsed.toString().equalsIgnoreCase(eventId) || generation < 0) {
                return Optional.empty();
            }
            return Optional.of(new MessageReference(eventId, generation));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void validateEnvelope(MessageEnvelope envelope, String expectedEventType) {
        if (envelope == null
                || envelope.eventId() == null
                || envelope.eventType() == null
                || envelope.occurredAt() == null
                || envelope.keyId() == null
                || envelope.nonce() == null
                || envelope.nonce().length != 12
                || envelope.ciphertext() == null) {
            throw new MessagePayloadException("INVALID_ENVELOPE", "Message envelope is incomplete");
        }
        if (envelope.schemaVersion() != 1) {
            throw new MessagePayloadException("UNSUPPORTED_VERSION", "Message schema version is unsupported");
        }
        if (!expectedEventType.equals(envelope.eventType())) {
            throw new MessagePayloadException("EVENT_TYPE_MISMATCH", "Message event type does not match queue");
        }
    }

    private int retryAttempt(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        if (value instanceof Number number) {
            return Math.max(0, Math.min(number.intValue(), topology.retryDelays().size()));
        }
        return 0;
    }

    private Message copyForRetry(Message original, MessageEnvelope envelope, int attempt) {
        return MessageBuilder.withBody(original.getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(envelope.eventId() + ":" + envelope.generation())
                .setHeader("cc4c-event-type", envelope.eventType())
                .setHeader(RETRY_HEADER, attempt)
                .build();
    }

    private Message copyForDead(Message original, MessageEnvelope envelope, String errorCode) {
        return MessageBuilder.withBody(original.getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(envelope.eventId() + ":" + envelope.generation())
                .setHeader("cc4c-event-type", envelope.eventType())
                .setHeader("cc4c-error-code", errorCode)
                .build();
    }

    private record MessageReference(String eventId, int generation) {
    }
}
