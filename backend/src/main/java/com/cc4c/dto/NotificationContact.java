package com.cc4c.dto;

/**
 * 内部通知投递使用的用户 ID 与邮箱地址，不作为公开用户资料响应。
 *
 * @param userId 用户 ID
 * @param email 账户邮箱地址
 */
public record NotificationContact(long userId, String email) {}
