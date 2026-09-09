package com.cc4c.config;

import com.cc4c.support.cache.BusinessCacheStore;
import com.cc4c.support.cache.RedisBusinessCacheStore;
import com.cc4c.support.monitoring.Cc4cMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 校验业务缓存与 Session 的 Redis 命名空间隔离，并按开关装配缓存连接。 */
@Configuration(proxyBeanMethods = false)
class BusinessCacheConfiguration {

    // 即使禁用缓存也校验隔离身份，避免下一次启用时复用 Session 命名空间。
    /**
     * 启动时拒绝缺失、非法或与 Session 相同的缓存命名空间；禁用缓存时仍执行校验。
     *
     * @param properties 对应组件的类型化配置
     * @param sessionNamespace 业务 Session 的 Redis 命名空间
     */
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

    /**
     * 缓存开启时使用指定 Redis 地址创建独立连接的缓存存储。
     *
     * @param metrics 统一指标记录器
     * @param redisUrl 缓存连接使用的 Redis 地址，不得记录凭据
     * @return 带缓存指标的 Redis 存储
     */
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
