package com.cc4c.shared;

import java.time.Instant;

record OutboxMessage(
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
        String errorCode
) {
    MessageEnvelope envelope() {
        return new MessageEnvelope(
                eventId, eventType, schemaVersion, generation, occurredAt, expiresAt,
                payloadKeyId, payloadNonce, payloadCiphertext);
    }
}
