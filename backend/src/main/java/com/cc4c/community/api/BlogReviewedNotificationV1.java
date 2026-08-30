package com.cc4c.community.api;

import java.time.Instant;

/** BlogReviewedNotificationV1 定义模块之间稳定、可验证的公开契约。 */
public record BlogReviewedNotificationV1(
        String recipientEmail, String blogId, String title, ReviewOutcome outcome, Instant reviewedAt) {
    /** ReviewOutcome 定义模块之间稳定、可验证的公开契约。 */
    public enum ReviewOutcome {
        APPROVED,
        DENIED
    }
}
