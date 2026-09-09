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

/** 查询消息摘要并执行业务约束下的人工恢复或忽略，依靠状态和旧代次条件防止并发覆盖。 */
@Service
public class AsyncMessageOperations {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    /**
     * 接入 Outbox 仓库及恢复时重新加密的载荷密码器。
     *
     * @param repository Outbox 持久化仓库
     * @param cipher AES-GCM 载荷加解密器
     */
    AsyncMessageOperations(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * 规范化可选状态并校验事件类型白名单，再分页查询消息摘要。
     *
     * @param statusValue 可选的消息状态筛选
     * @param eventType 三个 v1 事件类型之一
     * @param query 从 1 起算的页码与页大小
     * @return 不含载荷的消息摘要页
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
     * 只恢复可恢复状态且收件人可用、验证码未过期的事件；解密旧载荷，递增代次并以活动密钥重新加密后条件更新。
     *
     * @param eventId 异步事件唯一标识
     * @return 恢复到 PENDING 成功时为 true
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
     * 仅允许忽略可恢复失败状态，按旧代次条件更新并记录管理员 ID。
     *
     * @param eventId 异步事件唯一标识
     * @param actorId 执行人工操作的管理员 ID
     * @return 忽略成功时为 true
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
     * 按事件 ID 加载 Outbox 记录，不存在时抛出 404。
     *
     * @param eventId 异步事件唯一标识
     * @return 存在的 Outbox 记录
     */
    private OutboxMessage required(String eventId) {
        return repository
                .findByEventId(eventId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Async message does not exist"));
    }

    /**
     * 空筛选返回空值；非空值去空白后必须属于三个固定 v1 事件类型。
     *
     * @param eventType 三个 v1 事件类型之一
     * @return 允许的事件类型或空值
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
     * 构造提示刷新后再操作的 409 并发状态冲突异常。
     *
     * @return 状态或代次已变化的业务异常
     */
    private BusinessException stateChanged() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                BusinessCode.CONFLICT,
                "Message state changed; refresh before retrying the operation");
    }
}
