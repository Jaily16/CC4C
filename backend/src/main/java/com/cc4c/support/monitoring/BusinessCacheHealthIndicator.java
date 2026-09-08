package com.cc4c.support.monitoring;

import com.cc4c.support.cache.BusinessCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * BusinessCacheHealthIndicator 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
@Component("businessCacheHealthIndicator")
final class BusinessCacheHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");
    private final BusinessCache cache;

    /**
     * 创建 BusinessCacheHealthIndicator 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param cache 调用方提供的 {@code cache} 值
     */
    BusinessCacheHealthIndicator(BusinessCache cache) {
        this.cache = cache;
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @return 当前操作产生的 Health 结果
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
