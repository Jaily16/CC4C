package com.cc4c.community.api;

import java.time.Instant;

/** BlogSubmittedNotificationV1 定义模块之间稳定、可验证的公开契约。 */
public record BlogSubmittedNotificationV1(String recipientEmail, String blogId, String title, Instant submittedAt) {}
