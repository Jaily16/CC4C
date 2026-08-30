package com.cc4c.identity.api;

/** NotificationContact 定义模块之间稳定、可验证的公开契约。 */
public record NotificationContact(long userId, String email) {}
