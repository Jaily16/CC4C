package com.cc4c.identity.api;

/** UserSnapshot 定义模块之间稳定、可验证的公开契约。 */
public record UserSnapshot(long userId, String name, String avatar) {}
