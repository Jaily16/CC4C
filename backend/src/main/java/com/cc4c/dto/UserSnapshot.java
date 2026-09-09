package com.cc4c.dto;

/**
 * 供业务协作使用的用户 ID、昵称和头像快照，不包含认证凭据。
 *
 * @param userId 用户 ID
 * @param name 用户昵称
 * @param avatar 头像请求路径
 */
public record UserSnapshot(long userId, String name, String avatar) {}
