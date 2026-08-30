package com.cc4c.shared;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("securityRedisHealthIndicator")
final class SecurityRedisHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    SecurityRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping())
                    ? Health.up().build()
                    : Health.down().withDetail("reason", "unexpected_response").build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("reason", "unavailable").build();
        }
    }
}
