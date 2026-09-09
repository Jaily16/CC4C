package com.cc4c.dto;

import com.cc4c.entity.OutboxMessage;
import com.cc4c.support.messaging.AsyncEventTypes;
import com.cc4c.support.messaging.OutboxStatus;
import java.time.Instant;

/**
 * 供管理页面查询的异步消息摘要；不包含收件地址或加密载荷。
 *
 * @param eventId 异步事件唯一标识
 * @param eventType 带版本的事件类型
 * @param aggregateType 关联业务聚合类型
 * @param aggregateId 关联业务聚合标识
 * @param status 消息当前投递状态
 * @param publishAttempts 已尝试发布次数
 * @param consumeAttempts 已尝试消费次数
 * @param createdAt 记录创建时间
 * @param updatedAt 记录最近更新时间
 * @param failedAt 记录失败时间
 * @param errorCode 可展示的失败分类码
 * @param recoverable 当前消息是否满足人工恢复条件
 */
public record AsyncMessageSummary(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        OutboxStatus status,
        int publishAttempts,
        int consumeAttempts,
        Instant createdAt,
        Instant updatedAt,
        Instant failedAt,
        String errorCode,
        boolean recoverable) {
    /**
     * 从 Outbox 记录生成管理摘要；过期验证码及收件人不可用的消息不允许恢复。
     *
     * @param message 待展示的 Outbox 记录
     * @return 不含载荷的消息摘要
     */
    public static AsyncMessageSummary from(OutboxMessage message) {
        boolean expiredVerification = AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED.equals(message.eventType())
                && message.expiresAt() != null
                && !message.expiresAt().isAfter(Instant.now());
        return new AsyncMessageSummary(
                message.eventId(),
                message.eventType(),
                message.aggregateType(),
                message.aggregateId(),
                message.status(),
                message.publishAttempts(),
                message.consumeAttempts(),
                message.createdAt(),
                message.updatedAt(),
                message.failedAt(),
                message.errorCode(),
                message.status().recoverable()
                        && !expiredVerification
                        && !"RECIPIENT_UNAVAILABLE".equals(message.errorCode()));
    }
}
