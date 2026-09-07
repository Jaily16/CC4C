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

/**
 * 协调可靠消息的解密、幂等、业务处理、重试和死信确认流程。
 */
@Component
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

    /**
     * 创建 ReliableMessageProcessor 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param inbox 由容器注入的 InboxRepository 协作组件
     * @param outbox 由容器注入的 OutboxRepository 协作组件
     * @param cipher 调用方提供的 {@code cipher} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param publisher 调用方提供的 {@code publisher} 值
     * @param topology 调用方提供的 {@code topology} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     */
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

    /**
     * 创建 ReliableMessageProcessor 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param inbox 由容器注入的 InboxRepository 协作组件
     * @param outbox 由容器注入的 OutboxRepository 协作组件
     * @param cipher 调用方提供的 {@code cipher} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param publisher 调用方提供的 {@code publisher} 值
     * @param topology 调用方提供的 {@code topology} 值
     */
    public ReliableMessageProcessor(
            InboxRepository inbox,
            OutboxRepository outbox,
            MessagePayloadCipher cipher,
            ObjectMapper objectMapper,
            RabbitMessagePublisher publisher,
            MessagingTopology topology) {
        this(inbox, outbox, cipher, objectMapper, publisher, topology, Cc4cMetrics.disabled());
    }

    /**
     * 处理 ReliableMessageProcessor 的输入或消息，并沿用既有幂等、确认与失败恢复策略。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param expectedEventType 调用方提供的 {@code expectedEventType} 值
     * @param message 待处理的消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag RabbitMQ 当前投递的确认标识
     * @param handler 调用方提供的 {@code handler} 值
     * @throws IOException I/O 操作失败或消息确认无法完成时抛出
     */
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

    /**
     * 执行 ReliableMessageProcessor 中的 correlationFallback 职责，并保持既有权限、事务与副作用边界。
     *
     * @param message 待处理的消息及其属性
     * @return 按当前声明计算、查询或转换得到的结果
     */
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

    /**
     * 处理 ReliableMessageProcessor 的输入或消息，并沿用既有幂等、确认与失败恢复策略。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param expectedEventType 调用方提供的 {@code expectedEventType} 值
     * @param message 待处理的消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag RabbitMQ 当前投递的确认标识
     * @param handler 调用方提供的 {@code handler} 值
     * @throws IOException I/O 操作失败或消息确认无法完成时抛出
     */
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

    /**
     * 处理 ReliableMessageProcessor 的输入或消息，并沿用既有幂等、确认与失败恢复策略。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     * @param original 调用方提供的 {@code original} 值
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag RabbitMQ 当前投递的确认标识
     * @param handler 调用方提供的 {@code handler} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @param permanent 调用方提供的 {@code permanent} 值
     * @throws IOException I/O 操作失败或消息确认无法完成时抛出
     */
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

    /**
     * 执行 ReliableMessageProcessor 中的 deadWithoutHandler 职责，并保持既有权限、事务与副作用边界。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param envelope 调用方提供的 {@code envelope} 值
     * @param original 调用方提供的 {@code original} 值
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag RabbitMQ 当前投递的确认标识
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @throws IOException I/O 操作失败或消息确认无法完成时抛出
     */
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

    /**
     * 执行 ReliableMessageProcessor 中的 rejectMalformedEnvelope 职责，并保持既有权限、事务与副作用边界。
     *
     * @param consumerName 调用方提供的 {@code consumerName} 值
     * @param expectedEventType 调用方提供的 {@code expectedEventType} 值
     * @param original 调用方提供的 {@code original} 值
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag RabbitMQ 当前投递的确认标识
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @throws IOException I/O 操作失败或消息确认无法完成时抛出
     */
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
