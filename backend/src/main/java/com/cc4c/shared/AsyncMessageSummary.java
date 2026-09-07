package com.cc4c.shared;

import java.time.Instant;

/**
 * 以不可变结构承载共享基础设施计算或查询结果。
 *
 * @param eventId 异步事件的全局唯一标识
 * @param eventType 带版本的异步事件类型
 * @param aggregateType 调用方提供的 {@code aggregateType} 值
 * @param aggregateId 目标对象的稳定标识
 * @param status 当前对象或流程的有限状态
 * @param publishAttempts 调用方提供的 {@code publishAttempts} 值
 * @param consumeAttempts 调用方提供的 {@code consumeAttempts} 值
 * @param createdAt 当前操作使用的时间点
 * @param updatedAt 当前操作使用的时间点
 * @param failedAt 当前操作使用的时间点
 * @param errorCode 调用方提供的 {@code errorCode} 值
 * @param recoverable 调用方提供的 {@code recoverable} 值
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
     * 转换可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @param message 当前处理的消息或用户提示
     * @return 当前操作产生的 AsyncMessageSummary 结果
     */
    static AsyncMessageSummary from(OutboxMessage message) {
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
