package com.cc4c.config;

import com.cc4c.support.cache.BusinessCacheStore;
import com.cc4c.support.cache.RedisBusinessCacheStore;
import com.cc4c.support.monitoring.Cc4cMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配共享基础设施运行组件，并集中声明安全或基础设施策略。
 */
@Configuration(proxyBeanMethods = false)
class BusinessCacheConfiguration {

    // 即使禁用缓存也校验隔离身份，避免下一次启用时复用 Session 命名空间。
    /**
     * 创建 BusinessCacheConfiguration 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 由容器注入的 BusinessCacheProperties 协作组件
     * @param sessionNamespace 调用方提供的 {@code sessionNamespace} 值
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
     * 创建并配置 BusinessCacheConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param metrics 调用方提供的 {@code metrics} 值
     * @param redisUrl 调用方提供的 {@code redisUrl} 值
     * @return 当前操作产生的 BusinessCacheStore 结果
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
