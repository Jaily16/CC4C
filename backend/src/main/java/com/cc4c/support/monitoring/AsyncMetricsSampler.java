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

/** 定时查询 Outbox 与 Inbox 状态计数，为消息指标和健康检查提供共享快照。 */
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
     * 保存消息仓储及观测设置，并注册从当前快照取值的 Gauge。
     *
     * @param outbox Outbox 状态查询仓储
     * @param inbox Inbox 状态查询仓储
     * @param properties 该组件使用的观测或 Prometheus 设置
     * @param metrics 受控指标适配器
     */
    AsyncMetricsSampler(
            OutboxRepository outbox, InboxRepository inbox, ObservabilityProperties properties, Cc4cMetrics metrics) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.properties = properties;
        this.metrics = metrics;
        registerGauges();
    }

    /** 观测开启时更新消息状态和最老待发送年龄；查询失败保留上次成功快照，并记录失败类型与计数。 */
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
     * 读取最近一次采样状态。
     *
     * @return 当前原子引用中的采样快照
     */
    Snapshot snapshot() {
        return snapshot.get();
    }

    /**
     * 计算距最近成功采样的秒数；关闭观测返回 0，从未成功返回十亿秒以标记陈旧。
     *
     * @return 不小于 0 的快照年龄秒数
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

    /** 按消息状态注册数量指标，并注册最老待发送年龄和采样年龄指标。 */
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
     * 只保留已声明的状态，并将没有记录的状态补为 0。
     *
     * @param statuses 需要输出的状态名称集合
     * @param values 仓储返回的状态计数
     * @return 包含完整已声明状态集合的不可变计数映射
     */
    private Map<String, Long> withZeroValues(String[] statuses, Map<String, Long> values) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        for (String status : statuses) {
            normalized.put(status, values.getOrDefault(status, 0L));
        }
        return Map.copyOf(normalized);
    }

    /**
     * 同时保存最后成功的消息数量、积压年龄与本轮采样失败类型。
     *
     * @param outboxCounts 各 Outbox 状态的记录数
     * @param inboxCounts 各 Inbox 状态的记录数
     * @param oldestPendingSeconds 最老待发送消息的等待秒数
     * @param lastSuccess 最后成功采样时间；未成功时为 null
     * @param lastFailureType 本轮失败类型名；成功时为 null
     */
    record Snapshot(
            Map<String, Long> outboxCounts,
            Map<String, Long> inboxCounts,
            double oldestPendingSeconds,
            Instant lastSuccess,
            String lastFailureType) {
        /**
         * 构造尚未成功采样的空状态。
         *
         * @return 计数为空、无成功时间和失败类型的初始快照
         */
        static Snapshot initial() {
            return new Snapshot(Map.of(), Map.of(), 0.0, null, null);
        }
    }
}
