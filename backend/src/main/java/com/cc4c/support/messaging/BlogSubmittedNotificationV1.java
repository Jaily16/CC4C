package com.cc4c.support.messaging;

import java.time.Instant;

/**
 * 发给审核收件人的博客提交通知载荷。
 *
 * @param recipientEmail 通知收件邮箱，不得记录
 * @param blogId 博客 ID 的字符串表示
 * @param title 博客标题
 * @param submittedAt 博客提交时间
 */
public record BlogSubmittedNotificationV1(String recipientEmail, String blogId, String title, Instant submittedAt) {}
