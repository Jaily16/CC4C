package com.cc4c.support.messaging;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.dto.AsyncMessageSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;
import com.cc4c.entity.OutboxMessage;
import com.cc4c.repository.OutboxRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AsyncMessageOperations 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
public class AsyncMessageOperations {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    /**
     * 创建 AsyncMessageOperations 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param repository 由容器注入的 OutboxRepository 协作组件
     * @param cipher 调用方提供的 {@code cipher} 值
     */
    AsyncMessageOperations(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * 查询并返回 AsyncMessageOperations 中与 find 对应的数据，不改变业务状态。
     *
     * @param statusValue 调用方提供的 {@code statusValue} 值
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<AsyncMessageSummary> find(String statusValue, String eventType, PageQuery query) {
        OutboxStatus status = null;
        if (statusValue != null && !statusValue.isBlank()) {
            try {
                status = OutboxStatus.valueOf(statusValue.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "Invalid message status");
            }
        }
        return repository.findPage(status, normalizeType(eventType), query);
    }

    /**
     * 执行 AsyncMessageOperations 中的 retry 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean retry(String eventId) {
        OutboxMessage message = required(eventId);
        if (!message.status().recoverable() || "RECIPIENT_UNAVAILABLE".equals(message.errorCode())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, BusinessCode.CONFLICT, "Message state does not allow retry");
        }
        if (AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED.equals(message.eventType())
                && message.expiresAt() != null
                && !message.expiresAt().isAfter(Instant.now())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.UNPROCESSABLE_ENTITY,
                    "Expired verification messages cannot be retried");
        }
        byte[] plaintext = cipher.decrypt(message.envelope());
        int generation = message.generation() + 1;
        EncryptedMessagePayload encrypted = cipher.encryptBytes(
                message.eventId(),
                message.eventType(),
                message.schemaVersion(),
                generation,
                message.occurredAt(),
                message.expiresAt(),
                plaintext);
        if (repository.resetForManualRetry(message, encrypted, generation) != 1) {
            throw stateChanged();
        }
        return true;
    }

    /**
     * 执行 AsyncMessageOperations 中的 ignore 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventId 目标对象的稳定标识
     * @param actorId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean ignore(String eventId, String actorId) {
        OutboxMessage message = required(eventId);
        if (!message.status().recoverable()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, BusinessCode.CONFLICT, "Message state does not allow ignore");
        }
        if (repository.ignore(eventId, message.generation(), actorId) != 1) {
            throw stateChanged();
        }
        return true;
    }

    /**
     * 校验 AsyncMessageOperations 中与 required 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param eventId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private OutboxMessage required(String eventId) {
        return repository
                .findByEventId(eventId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Async message does not exist"));
    }

    /**
     * 按 AsyncMessageOperations 的既定规则转换输入，不记录凭据或敏感原文。
     *
     * @param eventType 调用方提供的 {@code eventType} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String normalizeType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        String value = eventType.trim();
        if (!java.util.Set.of(
                        AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                        AsyncEventTypes.BLOG_SUBMITTED,
                        AsyncEventTypes.BLOG_REVIEWED)
                .contains(value)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "Invalid message event type");
        }
        return value;
    }

    /**
     * 执行 AsyncMessageOperations 中的 stateChanged 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private BusinessException stateChanged() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                BusinessCode.CONFLICT,
                "Message state changed; refresh before retrying the operation");
    }
}
