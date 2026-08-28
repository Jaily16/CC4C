package com.cc4c.shared;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class TransactionalOutbox {
    private final OutboxRepository repository;
    private final MessagePayloadCipher cipher;

    TransactionalOutbox(OutboxRepository repository, MessagePayloadCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String append(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload,
            Instant occurredAt,
            Instant expiresAt) {
        return append(eventType, aggregateType, aggregateId, payload, occurredAt, expiresAt,
                OutboxStatus.PENDING, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String appendPermanentFailure(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload,
            Instant occurredAt,
            Instant expiresAt,
            String errorCode) {
        return append(eventType, aggregateType, aggregateId, payload, occurredAt, expiresAt,
                OutboxStatus.DEAD, errorCode);
    }

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
        EncryptedMessagePayload encrypted = cipher.encrypt(
                eventId, eventType, 1, 0, persistedOccurredAt, persistedExpiresAt, payload);
        try {
            repository.insert(
                    eventId, eventType, aggregateType, aggregateId, eventType,
                    persistedOccurredAt, persistedExpiresAt, encrypted, status, errorCode);
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    BusinessCode.SERVICE_UNAVAILABLE,
                    "异步消息暂时无法可靠受理");
        }
        return eventId;
    }

    private static Instant databaseTimestamp(Instant value) {
        return value == null ? null : Instant.ofEpochMilli(value.toEpochMilli());
    }
}
