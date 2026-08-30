package com.cc4c.shared;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("asyncMessagingHealthIndicator")
final class AsyncMessagingHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final AsyncMetricsSampler sampler;
    private final ObservabilityProperties properties;

    AsyncMessagingHealthIndicator(AsyncMetricsSampler sampler, ObservabilityProperties properties) {
        this.sampler = sampler;
        this.properties = properties;
    }

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
