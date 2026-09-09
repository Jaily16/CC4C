package com.cc4c.support.monitoring;

import com.cc4c.config.ObservabilityProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/** 根据异步消息采样快照判断积压、失败和采样新鲜度，不在健康请求中重新查询消息表。 */
@Component("asyncMessagingHealthIndicator")
final class AsyncMessagingHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final AsyncMetricsSampler sampler;
    private final ObservabilityProperties properties;

    /**
     * 保存消息快照采样器和观测开关。
     *
     * @param sampler 异步消息快照采样器
     * @param properties 该组件使用的观测或 Prometheus 设置
     */
    AsyncMessagingHealthIndicator(AsyncMetricsSampler sampler, ObservabilityProperties properties) {
        this.sampler = sampler;
        this.properties = properties;
    }

    /**
     * 观测关闭时报告 UP；采样失败、超过 30 秒未更新、待发送超过 60 秒或存在失败及死信时报告 DEGRADED。
     *
     * @return 包含积压和采样状态的健康结果
     */
    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        AsyncMetricsSampler.Snapshot snapshot = sampler.snapshot();
        long failed = snapshot.outboxCounts().getOrDefault("PUBLISH_FAILED", 0L);
        long dead = snapshot.outboxCounts().getOrDefault("DEAD", 0L);
        double age = sampler.ageSeconds();
        if (snapshot.lastFailureType() != null
                || age > 30.0
                || snapshot.oldestPendingSeconds() > 60.0
                || failed > 0
                || dead > 0) {
            return Health.status(DEGRADED)
                    .withDetail("samplerFresh", age <= 30.0)
                    .withDetail("oldestPendingSeconds", Math.round(snapshot.oldestPendingSeconds()))
                    .withDetail("publishFailed", failed)
                    .withDetail("dead", dead)
                    .build();
        }
        return Health.up().build();
    }
}
