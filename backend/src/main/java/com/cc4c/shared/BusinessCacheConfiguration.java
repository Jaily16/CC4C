package com.cc4c.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BusinessCacheConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "cc4c.cache", name = "enabled", havingValue = "true")
    BusinessCacheStore businessCacheStore(
            BusinessCacheProperties properties,
            Cc4cMetrics metrics,
            @Value("${spring.session.redis.namespace:}") String sessionNamespace) {
        if (properties.redisUrl() == null || properties.redisUrl().isBlank()) {
            throw new IllegalStateException("CC4C_CACHE_REDIS_URL is required when business cache is enabled");
        }
        if (properties.namespace() == null || properties.namespace().isBlank()) {
            throw new IllegalStateException("CC4C_CACHE_NAMESPACE is required when business cache is enabled");
        }
        if (!properties.namespace().matches("[A-Za-z0-9:_-]{3,120}")) {
            throw new IllegalStateException("CC4C cache namespace contains unsupported characters");
        }
        if (properties.namespace().equals(sessionNamespace)) {
            throw new IllegalStateException("CC4C cache namespace must differ from the Session namespace");
        }
        return new RedisBusinessCacheStore(properties.redisUrl(), metrics);
    }
}
