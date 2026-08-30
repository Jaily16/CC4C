package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class Cc4cMetricsTest {
    @Test
    void registersOnlyFixedLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Cc4cMetrics metrics = new Cc4cMetrics(registry, properties(true));
        metrics.increment("cc4c.security.authentication.attempts", "role", "user", "outcome", "failure");
        long started = metrics.start();
        metrics.record(
                "cc4c.mybatis.operations", started, "module", "catalog", "command", "select", "outcome", "success");

        assertEquals(
                1.0,
                registry.get("cc4c.security.authentication.attempts")
                        .tag("role", "user")
                        .tag("outcome", "failure")
                        .counter()
                        .count());
        assertEquals(
                1L,
                registry.get("cc4c.mybatis.operations")
                        .tag("module", "catalog")
                        .timer()
                        .count());
        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.increment(
                        "cc4c.security.authentication.attempts", "role", "user", "request_id", "client-request-1234"));
        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.increment("cc4c.unregistered.metric", "outcome", "success"));
    }

    @Test
    void keepsAspectFourSnapshotWhilePublishingRegionalCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessCacheMetrics cacheMetrics = new BusinessCacheMetrics(new Cc4cMetrics(registry, properties(true)));
        cacheMetrics.hit("catalog:home");
        cacheMetrics.miss("catalog:home");
        cacheMetrics.negativeHit("catalog:detail");

        assertEquals(1, cacheMetrics.snapshot().hits());
        assertEquals(1, cacheMetrics.snapshot().misses());
        assertEquals(1, cacheMetrics.snapshot().negativeHits());
        assertTrue(registry.get("cc4c.cache.requests").counters().size() >= 3);
    }

    private ObservabilityProperties properties(boolean enabled) {
        return new ObservabilityProperties(
                enabled, "test", "test_observer", "fixed-test-password-at-least-24-chars", Duration.ofSeconds(15), 100);
    }
}
