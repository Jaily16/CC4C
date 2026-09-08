package com.cc4c.support.monitoring;

import com.cc4c.config.ObservabilityProperties;
import com.cc4c.repository.InboxRepository;
import com.cc4c.repository.OutboxRepository;
import com.cc4c.support.messaging.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采集共享基础设施脱敏运行指标，不改变业务处理结果。
 */
@Component
final class AsyncMetricsSampler {
    private static final String[] OUTBOX_STATUSES =
            Arrays.stream(OutboxStatus.values()).map(Enum::name).toArray(String[]::new);
    private static final String[] INBOX_STATUSES = {"PROCESSING", "RETRY_WAIT", "DONE", "DEAD"};

    private final OutboxRepository outbox;
    private final InboxRepository inbox;
    private final ObservabilityProperties properties;
    private final Cc4cMetrics metrics;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.initial());

    /**
     * 创建 AsyncMetricsSampler 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param outbox 由容器注入的 OutboxRepository 协作组件
     * @param inbox 由容器注入的 InboxRepository 协作组件
     * @param properties 由容器注入的 ObservabilityProperties 协作组件
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    AsyncMetricsSampler(
            OutboxRepository outbox, InboxRepository inbox, ObservabilityProperties properties, Cc4cMetrics metrics) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.properties = properties;
        this.metrics = metrics;
        registerGauges();
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     */
    @Scheduled(fixedDelayString = "${cc4c.observability.messaging-sample-interval:15s}")
    void sample() {
        if (!properties.enabled()) {
            return;
        }
        try {
            Map<String, Long> outboxCounts = withZeroValues(OUTBOX_STATUSES, outbox.statusCounts());
            Map<String, Long> inboxCounts = withZeroValues(INBOX_STATUSES, inbox.statusCounts());
            snapshot.set(new Snapshot(outboxCounts, inboxCounts, outbox.oldestPendingSeconds(), Instant.now(), null));
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

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 当前操作产生的 Snapshot 结果
     */
    Snapshot snapshot() {
        return snapshot.get();
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 创建当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     */
    private void registerGauges() {
        for (String status : OUTBOX_STATUSES) {
            metrics.registerGauge(
                    "cc4c.messaging.outbox.messages",
                    this,
                    sampler -> sampler.snapshot().outboxCounts().getOrDefault(status, 0L),
                    "status",
                    status.toLowerCase(java.util.Locale.ROOT));
        }
        for (String status : INBOX_STATUSES) {
            metrics.registerGauge(
                    "cc4c.messaging.inbox.messages",
                    this,
                    sampler -> sampler.snapshot().inboxCounts().getOrDefault(status, 0L),
                    "status",
                    status.toLowerCase(java.util.Locale.ROOT));
        }
        metrics.registerGauge("cc4c.messaging.outbox.oldest.pending.seconds", this, sampler -> sampler.snapshot()
                .oldestPendingSeconds());
        metrics.registerGauge("cc4c.messaging.sampler.age.seconds", this, AsyncMetricsSampler::ageSeconds);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param statuses 调用方提供的 {@code statuses} 值
     * @param values 调用方提供的 {@code values} 值
     * @return 当前操作产生的 Map<String,Long> 结果
     */
    private Map<String, Long> withZeroValues(String[] statuses, Map<String, Long> values) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        for (String status : statuses) {
            normalized.put(status, values.getOrDefault(status, 0L));
        }
        return Map.copyOf(normalized);
    }

    /**
     * 以不可变结构承载共享基础设施计算或查询结果。
     *
     * @param outboxCounts 调用方提供的 {@code outboxCounts} 值
     * @param inboxCounts 调用方提供的 {@code inboxCounts} 值
     * @param oldestPendingSeconds 调用方提供的 {@code oldestPendingSeconds} 值
     * @param lastSuccess 调用方提供的 {@code lastSuccess} 值
     * @param lastFailureType 调用方提供的 {@code lastFailureType} 值
     */
    record Snapshot(
            Map<String, Long> outboxCounts,
            Map<String, Long> inboxCounts,
            double oldestPendingSeconds,
            Instant lastSuccess,
            String lastFailureType) {
        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @return 当前操作产生的 Snapshot 结果
         */
        static Snapshot initial() {
            return new Snapshot(Map.of(), Map.of(), 0.0, null, null);
        }
    }
}
