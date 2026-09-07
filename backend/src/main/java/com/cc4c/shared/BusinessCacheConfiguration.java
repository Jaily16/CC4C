package com.cc4c.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BusinessCacheConfiguration {

    // 即使禁用缓存也校验隔离身份，避免下一次启用时复用 Session 命名空间。
    BusinessCacheConfiguration(
            BusinessCacheProperties properties, @Value("${spring.session.redis.namespace:}") String sessionNamespace) {
        if (sessionNamespace == null || sessionNamespace.isBlank()) {
            throw new IllegalStateException("CC4C_SESSION_NAMESPACE is required");
        }
        if (properties.namespace() == null || properties.namespace().isBlank()) {
            throw new IllegalStateException("CC4C_CACHE_NAMESPACE is required");
        }
        if (!properties.namespace().matches("[A-Za-z0-9:_-]{3,120}")
                || !sessionNamespace.matches("[A-Za-z0-9:_-]{3,120}")) {
            throw new IllegalStateException("CC4C Redis namespaces contain unsupported characters");
        }
        if (properties.namespace().equals(sessionNamespace)) {
            throw new IllegalStateException("CC4C cache namespace must differ from the Session namespace");
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "cc4c.cache", name = "enabled", havingValue = "true")
    BusinessCacheStore businessCacheStore(Cc4cMetrics metrics, @Value("${spring.data.redis.url:}") String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            throw new IllegalStateException("CC4C_REDIS_URL is required when business cache is enabled");
        }
        // Session 与缓存共享同一地址，但保留独立连接和 namespace，避免改变缓存故障旁路语义。
        return new RedisBusinessCacheStore(redisUrl, metrics);
    }
}
