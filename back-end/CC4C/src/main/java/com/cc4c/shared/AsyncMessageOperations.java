package com.cc4c.shared;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AsyncMessageOperations {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    AsyncMessageOperations(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public PageResult<AsyncMessageSummary> find(
            String statusValue, String eventType, PageQuery query) {
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

    @Transactional
    public boolean retry(String eventId) {
        OutboxMessage message = required(eventId);
        if (!message.status().recoverable()
                || "RECIPIENT_UNAVAILABLE".equals(message.errorCode())) {
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
                message.eventId(), message.eventType(), message.schemaVersion(), generation,
                message.occurredAt(), message.expiresAt(), plaintext);
        if (repository.resetForManualRetry(message, encrypted, generation) != 1) {
            throw stateChanged();
        }
        return true;
    }

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

    private OutboxMessage required(String eventId) {
        return repository.findByEventId(eventId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Async message does not exist"));
    }

    private String normalizeType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        String value = eventType.trim();
        if (!java.util.Set.of(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                AsyncEventTypes.BLOG_SUBMITTED,
                AsyncEventTypes.BLOG_REVIEWED).contains(value)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "Invalid message event type");
        }
        return value;
    }

    private BusinessException stateChanged() {
        return new BusinessException(
                HttpStatus.CONFLICT, BusinessCode.CONFLICT,
                "Message state changed; refresh before retrying the operation");
    }
}
