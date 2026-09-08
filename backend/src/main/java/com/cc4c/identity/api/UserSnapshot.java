package com.cc4c.identity.api;

/**
 * 以不可变结构承载身份认证计算或查询结果。
 *
 * @param userId 目标对象的稳定标识
 * @param name 调用方提供的 {@code name} 值
 * @param avatar 调用方提供的 {@code avatar} 值
 */
public record UserSnapshot(long userId, String name, String avatar) {}
