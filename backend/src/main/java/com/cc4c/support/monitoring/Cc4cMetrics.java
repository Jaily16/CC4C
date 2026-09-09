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

/** 统一注册缓存、数据库、安全及消息指标，校验指标名、标签集合和标签值格式。 */
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
     * 保存指标注册表；只有观测开启且注册表存在时才实际记录。
     *
     * @param registry Micrometer 指标注册表，可为 null 以禁用记录
     * @param properties 该组件使用的观测或 Prometheus 设置
     */
    public Cc4cMetrics(MeterRegistry registry, ObservabilityProperties properties) {
        this.registry = registry;
        this.enabled = properties != null && properties.enabled() && registry != null;
    }

    /**
     * 创建不注册也不记录指标的适配器。
     *
     * @return 关闭指标写入的适配器
     */
    public static Cc4cMetrics disabled() {
        return new Cc4cMetrics(null, null);
    }

    /**
     * 取得单调时钟起点供后续耗时计算。
     *
     * @return System.nanoTime 返回的纳秒值
     */
    public long start() {
        return System.nanoTime();
    }

    /**
     * 校验指标与标签后递增计数器；观测关闭时直接返回。
     *
     * @param name 白名单中的指标名称
     * @param tags 按键、值交替排列的标签序列
     */
    public void increment(String name, String... tags) {
        if (!enabled) {
            return;
        }
        Counter.builder(name).tags(safeTags(name, tags)).register(registry).increment();
    }

    /**
     * 记录从指定单调时钟起点开始的非负耗时，并发布直方图和既定 SLO 桶。
     *
     * @param name 白名单中的指标名称
     * @param startedNanos System.nanoTime 取得的开始时间，单位为纳秒
     * @param tags 按键、值交替排列的标签序列
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
     * 按指标名及标签复用 Gauge 的数值引用，并写入最新值。
     *
     * @param name 白名单中的指标名称
     * @param value 待写入的 Gauge 数值
     * @param tags 按键、值交替排列的标签序列
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
     * 注册从指定对象和取值函数实时读取的 Gauge；观测关闭时不注册。
     *
     * @param <T> 被观测对象或查询结果的类型
     * @param name 白名单中的指标名称
     * @param observed Gauge 读取的对象
     * @param valueFunction 从被观测对象提取数值的函数
     * @param tags 按键、值交替排列的标签序列
     */
    public <T> void registerGauge(String name, T observed, ToDoubleFunction<T> valueFunction, String... tags) {
        if (!enabled) {
            return;
        }
        Gauge.builder(name, observed, valueFunction).tags(safeTags(name, tags)).register(registry);
    }

    /**
     * 校验标签成对、键不重复、值格式合规，且键集合与该指标白名单完全相同。
     *
     * @param name 白名单中的指标名称
     * @param tags 按键、值交替排列的标签序列
     * @return 通过校验的 Micrometer 标签
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
     * 用指标名和标签序列标识可更新的 Gauge。
     *
     * @param name 白名单中的指标名称
     * @param tags 已经规范化的标签键值序列
     */
    private record GaugeKey(String name, List<String> tags) {
        /**
         * 复制标签序列，避免外部修改 Gauge 的索引键。
         *
         * @param name 白名单中的指标名称
         * @param tags 需要复制为不可变列表的标签序列
         */
        private GaugeKey {
            tags = List.copyOf(tags);
        }
    }
}
