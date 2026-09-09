package com.cc4c.support.messaging;

import com.cc4c.common.CorrelationIds;
import com.cc4c.common.MessagePayloadException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

/**
 * 集中维护可靠消息的信封校验、引用解析、重试头和死信消息构造规则。
 */
public final class ReliableMessageProtocolSupport {
    private static final String RETRY_HEADER = "cc4c-retry-attempt";

    /**
     * 解析 AMQP messageId 中的事件 ID 与代次，要求规范 UUID 及非负整数代次。
     *
     * @param message 当前 AMQP 消息及其属性
     * @return 可用幂等引用；格式不合规时为空 Optional
     */
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

    /**
     * 检查信封必需字段、12 字节 nonce、模式版本 1 及与队列期待一致的事件类型。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param expectedEventType 当前队列允许的唯一事件类型
     */
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

    /**
     * 读取数值重试头并夹在 0 与上限之间；缺失或非数值头按首次投递处理。
     *
     * @param message 当前 AMQP 消息及其属性
     * @param retryLimit 允许的最大重试档位
     * @return 受限的重试档位
     */
    public int retryAttempt(Message message, int retryLimit) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        if (value instanceof Number number) {
            return Math.max(0, Math.min(number.intValue(), retryLimit));
        }
        return 0;
    }

    /**
     * 保留原始正文，重建持久消息的身份、关联 ID 和指定重试次数头，不复制所有原始头。
     *
     * @param original 保留原始正文的当前投递
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param attempt 新消息携带的重试次数
     * @return 下一档重试使用的消息
     */
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

    /**
     * 保留原始正文，重建持久消息的身份、关联 ID 及错误码头。
     *
     * @param original 保留原始正文的当前投递
     * @param envelope 包含事件元数据和加密载荷的信封
     * @param errorCode 不含敏感正文的失败分类码
     * @return 转发死信交换机的消息
     */
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

    /**
     * 可用于消费幂等记录的事件 ID 和代次引用。
     *
     * @param eventId 异步事件唯一标识
     * @param generation 事件代次，人工恢复时递增
     */
    public record MessageReference(String eventId, int generation) {}
}
