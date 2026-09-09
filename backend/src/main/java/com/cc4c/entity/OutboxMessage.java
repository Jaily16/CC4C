package com.cc4c.entity;

import com.cc4c.support.messaging.MessageEnvelope;
import com.cc4c.support.messaging.OutboxStatus;
import java.time.Instant;

/**
 * Outbox 持久化记录的读取快照，包含投递状态、重试计数和加密载荷元数据。
 *
 * @param id Outbox 数据库记录主键
 * @param eventId 异步事件唯一标识
 * @param correlationId 贯穿请求和消息处理的关联 ID
 * @param schemaVersion 消息信封模式版本
 * @param eventType 带版本的事件类型
 * @param aggregateType 关联业务聚合类型
 * @param aggregateId 关联业务聚合标识
 * @param routingKey RabbitMQ 发布路由键
 * @param generation 人工恢复后递增的消息代次
 * @param status 消息当前投递状态
 * @param publishAttempts 已尝试发布次数
 * @param consumeAttempts 已尝试消费次数
 * @param payloadKeyId 加密载荷使用的密钥标识
 * @param payloadNonce 加密载荷的随机 nonce 字节
 * @param payloadCiphertext 加密后的载荷字节
 * @param occurredAt 业务事件发生时间
 * @param expiresAt 消息业务有效期截止时间
 * @param createdAt 记录创建时间
 * @param updatedAt 记录最近更新时间
 * @param failedAt 记录失败时间
 * @param errorCode 可展示的失败分类码
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
     * 将事件版本、代次和加密载荷装入信封；信封构造器复制载荷字节，不执行解密。
     *
     * @return 用于发布的消息信封
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
