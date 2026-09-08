package com.cc4c.support.messaging;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.common.CorrelationIds;
import com.cc4c.repository.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TransactionalOutbox 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public class TransactionalOutbox {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    /**
     * 创建 TransactionalOutbox 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param repository 由容器注入的 OutboxRepository 协作组件
     * @param cipher 调用方提供的 {@code cipher} 值
     */
    TransactionalOutbox(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * 执行 TransactionalOutbox 中的 append 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param aggregateType 调用方提供的 {@code aggregateType} 值
     * @param aggregateId 目标对象的稳定标识
     * @param payload 调用方提供的 {@code payload} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String append(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload,
            Instant occurredAt,
            Instant expiresAt) {
        return append(
                eventType, aggregateType, aggregateId, payload, occurredAt, expiresAt, OutboxStatus.PENDING, null);
    }

    /**
     * 执行 TransactionalOutbox 中的 appendPermanentFailure 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param aggregateType 调用方提供的 {@code aggregateType} 值
     * @param aggregateId 目标对象的稳定标识
     * @param payload 调用方提供的 {@code payload} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String appendPermanentFailure(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload,
            Instant occurredAt,
            Instant expiresAt,
            String errorCode) {
        return append(
                eventType, aggregateType, aggregateId, payload, occurredAt, expiresAt, OutboxStatus.DEAD, errorCode);
    }

    /**
     * 执行 TransactionalOutbox 中的 append 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param aggregateType 调用方提供的 {@code aggregateType} 值
     * @param aggregateId 目标对象的稳定标识
     * @param payload 调用方提供的 {@code payload} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @param status 调用方提供的 {@code status} 值
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String append(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload,
            Instant occurredAt,
            Instant expiresAt,
            OutboxStatus status,
            String errorCode) {
        Instant persistedOccurredAt = databaseTimestamp(occurredAt);
        Instant persistedExpiresAt = databaseTimestamp(expiresAt);
        String eventId = UUID.randomUUID().toString();
        EncryptedMessagePayload encrypted =
                cipher.encrypt(eventId, eventType, 1, 0, persistedOccurredAt, persistedExpiresAt, payload);
        try {
            repository.insert(
                    eventId,
                    CorrelationIds.currentOr(eventId),
                    eventType,
                    aggregateType,
                    aggregateId,
                    eventType,
                    persistedOccurredAt,
                    persistedExpiresAt,
                    encrypted,
                    status,
                    errorCode);
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, BusinessCode.SERVICE_UNAVAILABLE, "异步消息暂时无法可靠受理");
        }
        return eventId;
    }

    /**
     * 执行 TransactionalOutbox 中的 databaseTimestamp 职责，并保持既有权限、事务与副作用边界。
     *
     * @param value 调用方提供的 {@code value} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private static Instant databaseTimestamp(Instant value) {
        return value == null ? null : Instant.ofEpochMilli(value.toEpochMilli());
    }
}
