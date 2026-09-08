package com.cc4c.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * RedisReadinessVerifier 负责组装运行时基础设施，并明确其边界和故障处理策略。
 */
@Component
public final class RedisReadinessVerifier implements ApplicationRunner {
    private final RedisConnectionFactory connectionFactory;

    /**
     * 创建 RedisReadinessVerifier 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param connectionFactory 调用方提供的 {@code connectionFactory} 值
     */
    public RedisReadinessVerifier(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 执行 RedisReadinessVerifier 中的 run 职责，并保持既有权限、事务与副作用边界。
     *
     * @param args 调用方提供的 {@code args} 值
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
