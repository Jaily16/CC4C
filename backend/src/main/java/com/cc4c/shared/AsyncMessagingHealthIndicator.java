package com.cc4c.shared;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * AsyncMessagingHealthIndicator 负责共享基础设施的一项明确运行职责，并保持现有外部行为不变。
 */
@Component("asyncMessagingHealthIndicator")
final class AsyncMessagingHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final AsyncMetricsSampler sampler;
    private final ObservabilityProperties properties;

    /**
     * 创建 AsyncMessagingHealthIndicator 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param sampler 调用方提供的 {@code sampler} 值
     * @param properties 由容器注入的 ObservabilityProperties 协作组件
     */
    AsyncMessagingHealthIndicator(AsyncMetricsSampler sampler, ObservabilityProperties properties) {
        this.sampler = sampler;
        this.properties = properties;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 当前操作产生的 Health 结果
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
