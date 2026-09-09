package com.cc4c.support.messaging;

import java.time.Instant;

/**
 * 发给作者的博客审核结果载荷，在事务 Outbox 中加密保存。
 *
 * @param recipientEmail 通知收件邮箱，不得记录
 * @param blogId 博客 ID 的字符串表示
 * @param title 博客标题
 * @param outcome 审核通过或拒绝结果
 * @param reviewedAt 审核完成时间
 */
public record BlogReviewedNotificationV1(
        String recipientEmail, String blogId, String title, ReviewOutcome outcome, Instant reviewedAt) {
    /** 区分博客审核通过和拒绝两种通知结果。 */
    public enum ReviewOutcome {
        APPROVED,
        DENIED
    }
}
