package com.cc4c.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cc4c.cache")
/** BusinessCacheProperties 绑定外部配置，并集中表达运行时约束和安全默认值。 */
public record BusinessCacheProperties(boolean enabled, String redisUrl, String namespace, boolean testCleanupEnabled) {}
