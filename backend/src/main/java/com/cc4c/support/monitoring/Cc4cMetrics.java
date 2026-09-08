package com.cc4c.support.monitoring;

import com.cc4c.config.ObservabilityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * Cc4cMetrics 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public final class Cc4cMetrics {
    private static final Duration[] SERVICE_LEVEL_OBJECTIVES = {
        Duration.ofMillis(50),
        Duration.ofMillis(100),
        Duration.ofMillis(250),
        Duration.ofMillis(500),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5)
    };
    private static final Map<String, Set<String>> ALLOWED_TAG_KEYS = Map.ofEntries(
            Map.entry("cc4c.cache.requests", Set.of("region", "outcome")),
            Map.entry("cc4c.cache.load.duration", Set.of("region", "outcome")),
            Map.entry("cc4c.cache.redis.operations", Set.of("operation", "outcome")),
            Map.entry("cc4c.mybatis.operations", Set.of("module", "command", "outcome")),
            Map.entry("cc4c.security.authentication.attempts", Set.of("role", "outcome")),
            Map.entry("cc4c.security.authorization.denials", Set.of("role", "reason")),
            Map.entry("cc4c.security.rate.limit.rejections", Set.of("scope")),
            Map.entry("cc4c.messaging.publish.duration", Set.of("event_type", "outcome")),
            Map.entry("cc4c.messaging.consume.duration", Set.of("event_type", "outcome")),
            Map.entry("cc4c.messaging.retries", Set.of("event_type", "stage")),
            Map.entry("cc4c.messaging.dead", Set.of("event_type", "error_code")),
            Map.entry("cc4c.messaging.duplicates", Set.of("event_type")),
            Map.entry("cc4c.messaging.expired", Set.of("event_type")),
            Map.entry("cc4c.messaging.sampler.failures", Set.of("outcome")),
            Map.entry("cc4c.messaging.outbox.messages", Set.of("status")),
            Map.entry("cc4c.messaging.inbox.messages", Set.of("status")),
            Map.entry("cc4c.messaging.outbox.oldest.pending.seconds", Set.of()),
            Map.entry("cc4c.messaging.sampler.age.seconds", Set.of()));

    private final MeterRegistry registry;
    private final boolean enabled;
    private final Map<GaugeKey, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

    /**
     * 创建 Cc4cMetrics 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param registry 调用方提供的 {@code registry} 值
     * @param properties 调用方提供的 {@code properties} 值
     */
    public Cc4cMetrics(MeterRegistry registry, ObservabilityProperties properties) {
        this.registry = registry;
        this.enabled = properties != null && properties.enabled() && registry != null;
    }

    /**
     * 执行 Cc4cMetrics 中的 disabled 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public static Cc4cMetrics disabled() {
        return new Cc4cMetrics(null, null);
    }

    /**
     * 执行 Cc4cMetrics 中的 start 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public long start() {
        return System.nanoTime();
    }

    /**
     * 执行 Cc4cMetrics 中的 increment 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param tags 调用方提供的 {@code tags} 值
     */
    public void increment(String name, String... tags) {
        if (!enabled) {
            return;
        }
        Counter.builder(name).tags(safeTags(name, tags)).register(registry).increment();
    }

    /**
     * 执行 Cc4cMetrics 中的 record 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param startedNanos 调用方提供的 {@code startedNanos} 值
     * @param tags 调用方提供的 {@code tags} 值
     */
    public void record(String name, long startedNanos, String... tags) {
        if (!enabled) {
            return;
        }
        Timer.builder(name)
                .tags(safeTags(name, tags))
                .publishPercentileHistogram()
                .serviceLevelObjectives(SERVICE_LEVEL_OBJECTIVES)
                .register(registry)
                .record(Math.max(0, System.nanoTime() - startedNanos), TimeUnit.NANOSECONDS);
    }

    /**
     * 执行 Cc4cMetrics 中的 setGauge 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param value 调用方提供的 {@code value} 值
     * @param tags 调用方提供的 {@code tags} 值
     */
    public void setGauge(String name, double value, String... tags) {
        if (!enabled) {
            return;
        }
        Tags safeTags = safeTags(name, tags);
        GaugeKey key = new GaugeKey(
                name,
                safeTags.stream()
                        .flatMap(tag -> java.util.stream.Stream.of(tag.getKey(), tag.getValue()))
                        .toList());
        AtomicReference<Double> reference = gauges.computeIfAbsent(key, ignored -> {
            AtomicReference<Double> created = new AtomicReference<>(0.0);
            Gauge.builder(name, created, AtomicReference::get).tags(safeTags).register(registry);
            return created;
        });
        reference.set(value);
    }

    /**
     * 变更 Cc4cMetrics 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param name 调用方提供的 {@code name} 值
     * @param observed 调用方提供的 {@code observed} 值
     * @param valueFunction 调用方提供的 {@code valueFunction} 值
     * @param tags 调用方提供的 {@code tags} 值
     */
    public <T> void registerGauge(String name, T observed, ToDoubleFunction<T> valueFunction, String... tags) {
        if (!enabled) {
            return;
        }
        Gauge.builder(name, observed, valueFunction).tags(safeTags(name, tags)).register(registry);
    }

    /**
     * 执行 Cc4cMetrics 中的 safeTags 职责，并保持既有权限、事务与副作用边界。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param tags 调用方提供的 {@code tags} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private Tags safeTags(String name, String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Metric tags must be key/value pairs");
        }
        Set<String> allowed = ALLOWED_TAG_KEYS.get(name);
        if (allowed == null) {
            throw new IllegalArgumentException("Metric name is not allowlisted");
        }
        Set<String> provided = new HashSet<>();
        for (int index = 0; index < tags.length; index += 2) {
            if (!tags[index].matches("[a-z][a-z0-9_.-]{0,31}")) {
                throw new IllegalArgumentException("Metric tag key is not allowlisted");
            }
            if (!provided.add(tags[index])) {
                throw new IllegalArgumentException("Metric tag key is duplicated");
            }
            if (!tags[index + 1].matches("[A-Za-z0-9_.:-]{1,80}")) {
                throw new IllegalArgumentException("Metric tag value is not allowlisted");
            }
        }
        if (!provided.equals(allowed)) {
            throw new IllegalArgumentException("Metric tag set is not allowlisted");
        }
        return Tags.of(tags);
    }

    /**
     * GaugeKey 以不可变字段承载共享基础设施数据并保持既有协议语义。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param tags 调用方提供的 {@code tags} 值
     */
    private record GaugeKey(String name, List<String> tags) {
        /**
         * 创建 GaugeKey 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
         *
         * @param name 调用方提供的 {@code name} 值
         * @param tags 调用方提供的 {@code tags} 值
         */
        private GaugeKey {
            tags = List.copyOf(tags);
        }
    }
}
