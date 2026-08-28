package com.cc4c.shared;

import java.time.Instant;

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
        boolean recoverable
) {
    static AsyncMessageSummary from(OutboxMessage message) {
        boolean expiredVerification = AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED.equals(message.eventType())
                && message.expiresAt() != null
                && !message.expiresAt().isAfter(Instant.now());
        return new AsyncMessageSummary(
                message.eventId(), message.eventType(), message.aggregateType(), message.aggregateId(),
                message.status(), message.publishAttempts(), message.consumeAttempts(), message.createdAt(),
                message.updatedAt(), message.failedAt(), message.errorCode(),
                message.status().recoverable()
                        && !expiredVerification
                        && !"RECIPIENT_UNAVAILABLE".equals(message.errorCode()));
    }
}
