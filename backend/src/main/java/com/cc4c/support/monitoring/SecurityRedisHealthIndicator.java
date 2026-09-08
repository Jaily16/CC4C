package com.cc4c.support.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * SecurityRedisHealthIndicator 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
@Component("securityRedisHealthIndicator")
final class SecurityRedisHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    /**
     * 创建 SecurityRedisHealthIndicator 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param connectionFactory 调用方提供的 {@code connectionFactory} 值
     */
    SecurityRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 Health 结果
     */
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
