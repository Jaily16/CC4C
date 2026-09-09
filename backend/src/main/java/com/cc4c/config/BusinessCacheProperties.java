package com.cc4c.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定业务缓存开关及其独立 Redis 命名空间。
 *
 * @param enabled 是否启用该配置对应的能力
 * @param namespace 当前能力的隔离命名空间
 */
@ConfigurationProperties(prefix = "cc4c.cache")
public record BusinessCacheProperties(boolean enabled, String namespace) {}
