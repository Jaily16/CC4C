package com.cc4c.entity;

import com.cc4c.support.messaging.MessageEnvelope;
import com.cc4c.support.messaging.OutboxStatus;
import java.time.Instant;

/**
 * OutboxMessage 以不可变结构承载共享基础设施数据，并保持现有字段语义。
 *
 * @param id 调用方提供的 {@code id} 值
 * @param eventId 异步事件的全局唯一标识
 * @param correlationId 目标对象的稳定标识
 * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
 * @param eventType 带版本的异步事件类型
 * @param aggregateType 调用方提供的 {@code aggregateType} 值
 * @param aggregateId 目标对象的稳定标识
 * @param routingKey 调用方提供的 {@code routingKey} 值
 * @param generation 调用方提供的 {@code generation} 值
 * @param status 当前对象或流程的有限状态
 * @param publishAttempts 调用方提供的 {@code publishAttempts} 值
 * @param consumeAttempts 调用方提供的 {@code consumeAttempts} 值
 * @param payloadKeyId 目标对象的稳定标识
 * @param payloadNonce 调用方提供的 {@code payloadNonce} 值
 * @param payloadCiphertext 调用方提供的 {@code payloadCiphertext} 值
 * @param occurredAt 当前操作使用的时间点
 * @param expiresAt 当前操作使用的时间点
 * @param createdAt 当前操作使用的时间点
 * @param updatedAt 当前操作使用的时间点
 * @param failedAt 当前操作使用的时间点
 * @param errorCode 调用方提供的 {@code errorCode} 值
 */
public record OutboxMessage(
        long id,
        String eventId,
        String correlationId,
        int schemaVersion,
        String eventType,
        String aggregateType,
        String aggregateId,
        String routingKey,
        int generation,
        OutboxStatus status,
        int publishAttempts,
        int consumeAttempts,
        String payloadKeyId,
        byte[] payloadNonce,
        byte[] payloadCiphertext,
        Instant occurredAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant failedAt,
        String errorCode) {
    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @return 当前操作产生的 MessageEnvelope 结果
     */
    public MessageEnvelope envelope() {
        return new MessageEnvelope(
                eventId,
                eventType,
                schemaVersion,
                generation,
                occurredAt,
                expiresAt,
                payloadKeyId,
                payloadNonce,
                payloadCiphertext);
    }
}
