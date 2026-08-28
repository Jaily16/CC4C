package com.cc4c.shared;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("businessCacheHealthIndicator")
final class BusinessCacheHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final BusinessCache cache;

    BusinessCacheHealthIndicator(BusinessCache cache) {
        this.cache = cache;
    }

    @Override
    public Health health() {
        BusinessCache.HealthSnapshot snapshot = cache.healthSnapshot();
        if (!snapshot.enabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        if (!snapshot.reachable() || snapshot.bypassing()) {
            return Health.status(DEGRADED)
                    .withDetail("enabled", true)
                    .withDetail("reachable", snapshot.reachable())
                    .withDetail("bypassing", snapshot.bypassing())
                    .build();
        }
        return Health.up().withDetail("enabled", true).build();
    }
}
