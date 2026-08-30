package com.cc4c.shared;

import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

/** 集中维护可靠消息的信封校验、引用解析、重试头和死信消息构造规则。 */
/** ReliableMessageProtocolSupport 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class ReliableMessageProtocolSupport {
    private static final String RETRY_HEADER = "cc4c-retry-attempt";

    /** 解析 AMQP messageId 中的事件 ID 与代际。 */
    public Optional<MessageReference> messageReference(Message message) {
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

    /** 校验消息信封版本、必需字段和队列事件类型。 */
    public void validateEnvelope(MessageEnvelope envelope, String expectedEventType) {
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

    /** 读取并限制消息重试次数，非法或缺失头按首次投递处理。 */
    public int retryAttempt(Message message, int retryLimit) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        if (value instanceof Number number) {
            return Math.max(0, Math.min(number.intValue(), retryLimit));
        }
        return 0;
    }

    /** 构造保留原始正文、关联 ID 和递增重试头的持久消息。 */
    public Message copyForRetry(Message original, MessageEnvelope envelope, int attempt) {
        return MessageBuilder.withBody(original.getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(envelope.eventId() + ":" + envelope.generation())
                .setHeader("cc4c-event-type", envelope.eventType())
                .setHeader(CorrelationIds.AMQP_HEADER, CorrelationIds.currentOr(envelope.eventId()))
                .setHeader(RETRY_HEADER, attempt)
                .build();
    }

    /** 构造保留原始正文、关联 ID 和错误码的持久死信消息。 */
    public Message copyForDead(Message original, MessageEnvelope envelope, String errorCode) {
        return MessageBuilder.withBody(original.getBody())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(envelope.eventId() + ":" + envelope.generation())
                .setHeader("cc4c-event-type", envelope.eventType())
                .setHeader(CorrelationIds.AMQP_HEADER, CorrelationIds.currentOr(envelope.eventId()))
                .setHeader("cc4c-error-code", errorCode)
                .build();
    }

    /** 可用于幂等持久化的消息引用。 */
    /** MessageReference 是不可变的数据载体，保持现有字段语义和序列化契约。 */
    public record MessageReference(String eventId, int generation) {}
}
