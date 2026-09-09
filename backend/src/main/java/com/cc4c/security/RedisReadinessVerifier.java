package com.cc4c.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** 启动时检查安全 Redis 连接能否返回 PONG；异常直接阻止启动检查通过。 */
@Component
public final class RedisReadinessVerifier implements ApplicationRunner {
    private final RedisConnectionFactory connectionFactory;

    /**
     * 保存安全 Redis 连接工厂。
     *
     * @param connectionFactory 安全 Redis 连接工厂
     */
    public RedisReadinessVerifier(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 获取连接执行 PING 并验证 PONG，退出时关闭本次连接。
     *
     * @param args Spring 启动参数，本检查不使用其内容
     */
    @Override
    public void run(ApplicationArguments args) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                throw new IllegalStateException("Redis readiness check did not return PONG");
            }
        }
    }
}
