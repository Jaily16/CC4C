package com.cc4c.support.messaging;

import com.cc4c.common.CorrelationIds;
import com.cc4c.common.MailDeliveryException;
import com.cc4c.common.MessagePayloadException;
import com.cc4c.repository.InboxRepository;
import com.cc4c.repository.OutboxRepository;
import com.cc4c.support.monitoring.Cc4cMetrics;
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
     * 接入幂等及 Outbox 仓库、密码器、发布器、拓扑和指标，创建协议辅助。
     *
     * @param inbox 消费幂等与租约仓库
     * @param outbox Outbox 持久化仓库
     * @param cipher AES-GCM 载荷加解密器
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param publisher 等待 broker 确认的消息发布器
     * @param topology 消息队列与交换机名称规则
     * @param metrics 发布消费指标记录器
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
     * 委托主构造器并使用禁用指标实现。
     *
     * @param inbox 消费幂等与租约仓库
     * @param outbox Outbox 持久化仓库
     * @param cipher AES-GCM 载荷加解密器
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param publisher 等待 broker 确认的消息发布器
     * @param topology 消息队列与交换机名称规则
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
     * 为本次消费建立关联 ID 作用域并记录耗时，结束时恢复线程 MDC；异常记录后继续传播。
     *
     * @param consumerName 消费幂等记录的固定消费者名称
     * @param expectedEventType 当前队列允许的唯一事件类型
     * @param message 当前 AMQP 消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @param handler 已解密消息的业务处理和清理回调
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 优先从合法 AMQP messageId 提取事件 ID，否则仅尝试解析大小受限的信封。
     *
     * @param message 当前 AMQP 消息及其属性
     * @return 可用事件 ID；无法解析时为空
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
     * 校验和解密信封后领取五分钟消费租约；重复或正在处理的投递直接 ACK，处理成功落库后再 ACK。
     *
     * @param consumerName 消费幂等记录的固定消费者名称
     * @param expectedEventType 当前队列允许的唯一事件类型
     * @param message 当前 AMQP 消息及其属性
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @param handler 已解密消息的业务处理和清理回调
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 临时错误转发到下一重试队列，永久或耗尽错误写死信并回调；转发确认才 ACK，否则 NACK 并重新入队。
     *
     * @param consumerName 消费幂等记录的固定消费者名称
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param plaintext 解密后的敏感载荷字节，不得记录
     * @param original 保留原始正文的当前投递
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @param handler 已解密消息的业务处理和清理回调
     * @param errorCode 不含敏感正文的失败分类码
     * @param permanent 是否跳过自动重试直接进入死信
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 为可解析但无效或解密失败的信封领取短租约，标记死信并转发，不调用业务处理器。
     *
     * @param consumerName 消费幂等记录的固定消费者名称
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param original 保留原始正文的当前投递
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @param errorCode 不含敏感正文的失败分类码
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
     * 尽量从 messageId 记录失败状态，再以不重新入队的 basicReject 交给队列死信机制。
     *
     * @param consumerName 消费幂等记录的固定消费者名称
     * @param expectedEventType 当前队列允许的唯一事件类型
     * @param original 保留原始正文的当前投递
     * @param channel 用于确认或拒绝投递的 RabbitMQ 通道
     * @param deliveryTag 当前通道中的投递确认标识
     * @param errorCode 不含敏感正文的失败分类码
     * @throws IOException AMQP 确认、拒绝或相关 I/O 操作无法完成时抛出
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
