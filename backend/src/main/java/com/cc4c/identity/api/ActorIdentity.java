package com.cc4c.identity.api;

/** ActorIdentity 定义模块之间稳定、可验证的公开契约。 */
public record ActorIdentity(AccountRole role, String id, String displayName) {}
