package com.cc4c.security;

/**
 * 供业务服务使用的当前身份快照，包含角色、ID 和展示名。
 *
 * @param role 业务角色 USER 或 ADMIN
 * @param id 该角色下的身份 ID
 * @param displayName 身份展示名
 */
public record ActorIdentity(AccountRole role, String id, String displayName) {}
