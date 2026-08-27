package com.cc4c.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cc4c.cache")
public record BusinessCacheProperties(
        boolean enabled,
        String redisUrl,
        String namespace,
        boolean testCleanupEnabled
) {
}
