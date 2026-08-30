package com.cc4c.shared;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cc4c.security", name = "redis-readiness-enabled", havingValue = "true")
/** RedisReadinessVerifier 负责组装运行时基础设施，并明确其边界和故障处理策略。 */
public final class RedisReadinessVerifier implements ApplicationRunner {
    private final RedisConnectionFactory connectionFactory;

    public RedisReadinessVerifier(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

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
