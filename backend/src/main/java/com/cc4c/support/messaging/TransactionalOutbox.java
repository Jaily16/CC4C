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

/** 要求参与已有业务事务，先绑定数据库毫秒精度元数据并加密，再将事件追加到 Outbox。 */
@Component
public class TransactionalOutbox {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    /**
     * 接入 Outbox 数据访问及消息载荷密码器。
     *
     * @param repository Outbox 持久化仓库
     * @param cipher AES-GCM 载荷加解密器
     */
    TransactionalOutbox(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * 在已有业务事务中追加初始 PENDING 的加密事件，不在当前请求直接发布。
     *
     * @param eventType 三个 v1 事件类型之一
     * @param aggregateType 关联业务聚合类型
     * @param aggregateId 关联业务聚合标识
     * @param payload 待 JSON 编码并加密的业务载荷
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @return 新生成的事件 ID
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
     * 在已有事务中追加初始 DEAD 事件，保留永久失败原因而不申请发布。
     *
     * @param eventType 三个 v1 事件类型之一
     * @param aggregateType 关联业务聚合类型
     * @param aggregateId 关联业务聚合标识
     * @param payload 待 JSON 编码并加密的业务载荷
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param errorCode 不含敏感正文的失败分类码
     * @return 新生成的事件 ID
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
     * 将时间对齐毫秒精度，生成事件 ID 并加密，插入记录；数据访问失败转为 503。
     *
     * @param eventType 三个 v1 事件类型之一
     * @param aggregateType 关联业务聚合类型
     * @param aggregateId 关联业务聚合标识
     * @param payload 待 JSON 编码并加密的业务载荷
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param status 新记录的初始投递状态
     * @param errorCode 不含敏感正文的失败分类码
     * @return 本次追加的事件 ID
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
     * 将时间截取为数据库使用的毫秒精度，确保加密 AAD 与持久化后读取值一致。
     *
     * @param value 可空的待对齐精度时间
     * @return 毫秒精度时间；输入为空时仍为空
     */
    private static Instant databaseTimestamp(Instant value) {
        return value == null ? null : Instant.ofEpochMilli(value.toEpochMilli());
    }
}
