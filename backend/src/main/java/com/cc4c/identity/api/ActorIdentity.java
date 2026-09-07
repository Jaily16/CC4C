package com.cc4c.identity.api;

/**
 * ActorIdentity 以不可变结构承载身份认证数据，并保持现有字段语义。
 *
 * @param role 当前身份的固定角色
 * @param id 调用方提供的 {@code id} 值
 * @param displayName 调用方提供的 {@code displayName} 值
 */
public record ActorIdentity(AccountRole role, String id, String displayName) {}
