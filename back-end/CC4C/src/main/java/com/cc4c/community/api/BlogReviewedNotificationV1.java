package com.cc4c.community.api;

import java.time.Instant;

public record BlogReviewedNotificationV1(
        String recipientEmail,
        String blogId,
        String title,
        ReviewOutcome outcome,
        Instant reviewedAt
) {
    public enum ReviewOutcome {
        APPROVED,
        DENIED
    }
}
