package com.cc4c.shared;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncMetricsSamplerTest {
    @Test
    void samplesDatabaseOnceAndServesGaugesFromMemory() {
        OutboxRepository outbox = mock(OutboxRepository.class);
        InboxRepository inbox = mock(InboxRepository.class);
        when(outbox.statusCounts()).thenReturn(Map.of("PENDING", 3L, "DEAD", 1L));
        when(inbox.statusCounts()).thenReturn(Map.of("DONE", 5L));
        when(outbox.oldestPendingSeconds()).thenReturn(75.0);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityProperties properties = properties(true);
        AsyncMetricsSampler sampler = new AsyncMetricsSampler(
                outbox, inbox, properties, new Cc4cMetrics(registry, properties));

        sampler.sample();

        verify(outbox).statusCounts();
        verify(inbox).statusCounts();
        verify(outbox).oldestPendingSeconds();
        assertEquals(3.0, registry.get("cc4c.messaging.outbox.messages")
                .tag("status", "pending").gauge().value());
        assertEquals(5.0, registry.get("cc4c.messaging.inbox.messages")
                .tag("status", "done").gauge().value());
        assertEquals("DEGRADED",
                new AsyncMessagingHealthIndicator(sampler, properties).health().getStatus().getCode());
    }

    @Test
    void disabledSamplerDoesNotQueryDatabase() {
        OutboxRepository outbox = mock(OutboxRepository.class);
        InboxRepository inbox = mock(InboxRepository.class);
        ObservabilityProperties properties = properties(false);
        AsyncMetricsSampler sampler = new AsyncMetricsSampler(
                outbox, inbox, properties, Cc4cMetrics.disabled());

        sampler.sample();

        assertEquals(0.0, sampler.ageSeconds());
        assertTrue(new AsyncMessagingHealthIndicator(sampler, properties).health()
                .getStatus().equals(org.springframework.boot.actuate.health.Status.UP));
    }

    private ObservabilityProperties properties(boolean enabled) {
        return new ObservabilityProperties(
                enabled,
                "test",
                "test_observer",
                "fixed-test-password-at-least-24-chars",
                Duration.ofSeconds(15),
                100);
    }
}
