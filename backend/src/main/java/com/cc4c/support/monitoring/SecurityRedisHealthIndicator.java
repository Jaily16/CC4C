package com.cc4c.support.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** 通过独立 Redis PING 检查认证和 Session 所依赖的 Redis 可达性。 */
@Component("securityRedisHealthIndicator")
final class SecurityRedisHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    /**
     * 保存获取健康探测连接的 Redis 连接工厂。
     *
     * @param connectionFactory 安全 Redis 连接工厂
     */
    SecurityRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * 打开连接并发送 PING，结束后关闭；异常只映射为 unavailable。
     *
     * @return PONG 对应 UP，异常或其他响应对应 DOWN
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
