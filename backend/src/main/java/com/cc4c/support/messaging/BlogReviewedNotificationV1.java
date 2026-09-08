package com.cc4c.support.messaging;

import java.time.Instant;

/**
 * BlogReviewedNotificationV1 以不可变结构承载博客社区数据，并保持现有字段语义。
 *
 * @param recipientEmail 调用方提供的 {@code recipientEmail} 值
 * @param blogId 目标对象的稳定标识
 * @param title 当前博客或课程的标题
 * @param outcome 当前处理完成后的受控结果
 * @param reviewedAt 当前操作使用的时间点
 */
public record BlogReviewedNotificationV1(
        String recipientEmail, String blogId, String title, ReviewOutcome outcome, Instant reviewedAt) {
    /**
     * ReviewOutcome 枚举博客社区的有限状态或协议取值。
     */
    public enum ReviewOutcome {
        APPROVED,
        DENIED
    }
}
