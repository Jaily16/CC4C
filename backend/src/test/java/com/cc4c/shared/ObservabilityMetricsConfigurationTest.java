package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObservabilityMetricsConfigurationTest {
    @Test
    void deniesHttpUriTagsBeyondConfiguredLimit() {
        ObservabilityProperties properties = new ObservabilityProperties(
                true, "test", "test_observer", "fixed-test-password-at-least-24-chars", Duration.ofSeconds(15), 10);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new ObservabilityMetricsConfiguration().httpUriCardinalityFilter(properties));

        for (int index = 0; index < 11; index++) {
            Counter.builder("http.server.requests")
                    .tag("uri", "/fixed-route-" + index)
                    .register(registry)
                    .increment();
        }

        assertEquals(10, registry.getMeters().size());
    }
}
