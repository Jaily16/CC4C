package com.cc4c.shared;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
final class AsyncMetricsSampler {
    private static final String[] OUTBOX_STATUSES = Arrays.stream(OutboxStatus.values())
            .map(Enum::name)
            .toArray(String[]::new);
    private static final String[] INBOX_STATUSES = {
            "PROCESSING", "RETRY_WAIT", "DONE", "DEAD"
    };

    private final OutboxRepository outbox;
    private final InboxRepository inbox;
    private final ObservabilityProperties properties;
    private final Cc4cMetrics metrics;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.initial());

    AsyncMetricsSampler(
            OutboxRepository outbox,
            InboxRepository inbox,
            ObservabilityProperties properties,
            Cc4cMetrics metrics) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.properties = properties;
        this.metrics = metrics;
        registerGauges();
    }

    @Scheduled(fixedDelayString = "${cc4c.observability.messaging-sample-interval:15s}")
    void sample() {
        if (!properties.enabled()) {
            return;
        }
        try {
            Map<String, Long> outboxCounts = withZeroValues(OUTBOX_STATUSES, outbox.statusCounts());
            Map<String, Long> inboxCounts = withZeroValues(INBOX_STATUSES, inbox.statusCounts());
            snapshot.set(new Snapshot(
                    outboxCounts,
                    inboxCounts,
                    outbox.oldestPendingSeconds(),
                    Instant.now(),
                    null));
        } catch (RuntimeException exception) {
            Snapshot previous = snapshot.get();
            snapshot.set(new Snapshot(
                    previous.outboxCounts(),
                    previous.inboxCounts(),
                    previous.oldestPendingSeconds(),
                    previous.lastSuccess(),
                    exception.getClass().getSimpleName()));
            metrics.increment("cc4c.messaging.sampler.failures", "outcome", "error");
        }
    }

    Snapshot snapshot() {
        return snapshot.get();
    }

    double ageSeconds() {
        Instant lastSuccess = snapshot.get().lastSuccess();
        if (!properties.enabled()) {
            return 0.0;
        }
        if (lastSuccess == null) {
            return 1_000_000_000.0;
        }
        return Math.max(0.0, Duration.between(lastSuccess, Instant.now()).toMillis() / 1000.0);
    }

    private void registerGauges() {
        for (String status : OUTBOX_STATUSES) {
            metrics.registerGauge("cc4c.messaging.outbox.messages", this,
                    sampler -> sampler.snapshot().outboxCounts().getOrDefault(status, 0L),
                    "status", status.toLowerCase(java.util.Locale.ROOT));
        }
        for (String status : INBOX_STATUSES) {
            metrics.registerGauge("cc4c.messaging.inbox.messages", this,
                    sampler -> sampler.snapshot().inboxCounts().getOrDefault(status, 0L),
                    "status", status.toLowerCase(java.util.Locale.ROOT));
        }
        metrics.registerGauge("cc4c.messaging.outbox.oldest.pending.seconds", this,
                sampler -> sampler.snapshot().oldestPendingSeconds());
        metrics.registerGauge("cc4c.messaging.sampler.age.seconds", this,
                AsyncMetricsSampler::ageSeconds);
    }

    private Map<String, Long> withZeroValues(String[] statuses, Map<String, Long> values) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        for (String status : statuses) {
            normalized.put(status, values.getOrDefault(status, 0L));
        }
        return Map.copyOf(normalized);
    }

    record Snapshot(
            Map<String, Long> outboxCounts,
            Map<String, Long> inboxCounts,
            double oldestPendingSeconds,
            Instant lastSuccess,
            String lastFailureType) {
        static Snapshot initial() {
            return new Snapshot(Map.of(), Map.of(), 0.0, null, null);
        }
    }
}
