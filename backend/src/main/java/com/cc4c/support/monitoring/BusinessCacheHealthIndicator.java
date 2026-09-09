package com.cc4c.support.monitoring;

import com.cc4c.support.cache.BusinessCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/** 将业务缓存的可达性与旁路状态映射为健康结果。 */
@Component("businessCacheHealthIndicator")
final class BusinessCacheHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final BusinessCache cache;

    /**
     * 保存提供健康快照的业务缓存协调器。
     *
     * @param cache 业务缓存健康快照提供者
     */
    BusinessCacheHealthIndicator(BusinessCache cache) {
        this.cache = cache;
    }

    /**
     * 缓存关闭时报告 UP；开启但不可达或处于旁路时报告 DEGRADED。
     *
     * @return 包含开启、可达和旁路状态的健康结果
     */
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
