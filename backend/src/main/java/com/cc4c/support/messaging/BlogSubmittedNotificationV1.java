package com.cc4c.support.messaging;

import java.time.Instant;

/**
 * BlogSubmittedNotificationV1 以不可变结构承载博客社区数据，并保持现有字段语义。
 *
 * @param recipientEmail 调用方提供的 {@code recipientEmail} 值
 * @param blogId 目标对象的稳定标识
 * @param title 当前博客或课程的标题
 * @param submittedAt 当前操作使用的时间点
 */
public record BlogSubmittedNotificationV1(String recipientEmail, String blogId, String title, Instant submittedAt) {}
