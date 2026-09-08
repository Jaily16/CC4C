package com.cc4c.support.monitoring;

import java.util.concurrent.atomic.LongAdder;

/**
 * 采集共享基础设施脱敏运行指标，不改变业务处理结果。
 */
public final class BusinessCacheMetrics {
    private final Cc4cMetrics micrometer;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder negativeHits = new LongAdder();
    private final LongAdder loads = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder bypasses = new LongAdder();
    private final LongAdder lockWaits = new LongAdder();

    /**
     * 创建 BusinessCacheMetrics 实例，不触发外部 I/O。
     */
    public BusinessCacheMetrics() {
        this(Cc4cMetrics.disabled());
    }

    /**
     * 创建 BusinessCacheMetrics 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param micrometer 调用方提供的 {@code micrometer} 值
     */
    public BusinessCacheMetrics(Cc4cMetrics micrometer) {
        this.micrometer = micrometer;
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void hit(String region) {
        hits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "hit");
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void miss(String region) {
        misses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "miss");
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void negativeHit(String region) {
        negativeHits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "negative");
    }

    /**
     * 读取业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     * @param startedNanos 调用方提供的 {@code startedNanos} 值
     * @param outcome 当前处理完成后的受控结果
     */
    public void load(String region, long startedNanos, String outcome) {
        loads.increment();
        micrometer.record("cc4c.cache.load.duration", startedNanos, "region", region, "outcome", outcome);
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void error(String region) {
        errors.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "error");
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void bypass(String region) {
        bypasses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "bypass");
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param region 业务缓存区域名称
     */
    public void lockWait(String region) {
        lockWaits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "lock_wait");
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @return 当前操作产生的 Snapshot 结果
     */
    public Snapshot snapshot() {
        return new Snapshot(
                hits.sum(),
                misses.sum(),
                negativeHits.sum(),
                loads.sum(),
                errors.sum(),
                bypasses.sum(),
                lockWaits.sum());
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     */
    public void reset() {
        hits.reset();
        misses.reset();
        negativeHits.reset();
        loads.reset();
        errors.reset();
        bypasses.reset();
        lockWaits.reset();
    }

    /**
     * 以不可变结构承载共享基础设施计算或查询结果。
     *
     * @param hits 调用方提供的 {@code hits} 值
     * @param misses 调用方提供的 {@code misses} 值
     * @param negativeHits 调用方提供的 {@code negativeHits} 值
     * @param loads 调用方提供的 {@code loads} 值
     * @param errors 调用方提供的 {@code errors} 值
     * @param bypasses 调用方提供的 {@code bypasses} 值
     * @param lockWaits 调用方提供的 {@code lockWaits} 值
     */
    public record Snapshot(
            long hits, long misses, long negativeHits, long loads, long errors, long bypasses, long lockWaits) {
        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         *
         * @return 按当前规则计算或读取的数值
         */
        public double positiveHitRatio() {
            long attempts = hits + misses;
            return attempts == 0 ? 0.0 : (double) hits / attempts;
        }
    }
}
