package com.cc4c.community.api;

import java.time.Instant;

public record BlogSubmittedNotificationV1(
        String recipientEmail,
        String blogId,
        String title,
        Instant submittedAt
) {
}
