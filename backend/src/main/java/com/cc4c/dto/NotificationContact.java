package com.cc4c.dto;

/**
 * NotificationContact 以不可变结构承载身份认证数据，并保持现有字段语义。
 *
 * @param userId 目标对象的稳定标识
 * @param email 用于账户或通知流程的邮箱地址
 */
public record NotificationContact(long userId, String email) {}
