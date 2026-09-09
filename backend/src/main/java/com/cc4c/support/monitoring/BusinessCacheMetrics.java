package com.cc4c.support.monitoring;

import java.util.concurrent.atomic.LongAdder;

/** 同时维护进程内缓存计数和 Micrometer 指标，供缓存快照与观测展示使用。 */
public final class BusinessCacheMetrics {
    private final Cc4cMetrics micrometer;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder negativeHits = new LongAdder();
    private final LongAdder loads = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder bypasses = new LongAdder();
    private final LongAdder lockWaits = new LongAdder();

    /** 创建仅维护本地计数、不向 Micrometer 注册指标的实例。 */
    public BusinessCacheMetrics() {
        this(Cc4cMetrics.disabled());
    }

    /**
     * 保存可选启用的 Micrometer 指标适配器。
     *
     * @param micrometer 向 Micrometer 写入的指标适配器
     */
    public BusinessCacheMetrics(Cc4cMetrics micrometer) {
        this.micrometer = micrometer;
    }

    /**
     * 记录一次正向缓存命中，并按区域增加 hit 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void hit(String region) {
        hits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "hit");
    }

    /**
     * 记录一次缓存未命中，并按区域增加 miss 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void miss(String region) {
        misses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "miss");
    }

    /**
     * 记录一次空值缓存命中，并按区域增加 negative 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void negativeHit(String region) {
        negativeHits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "negative");
    }

    /**
     * 增加加载计数，并从纳秒起点记录数据源加载耗时和结果。
     *
     * @param region 业务缓存区域名称
     * @param startedNanos System.nanoTime 取得的开始时间，单位为纳秒
     * @param outcome 该操作的受控结果标签
     */
    public void load(String region, long startedNanos, String outcome) {
        loads.increment();
        micrometer.record("cc4c.cache.load.duration", startedNanos, "region", region, "outcome", outcome);
    }

    /**
     * 记录一次缓存操作错误，并按区域增加 error 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void error(String region) {
        errors.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "error");
    }

    /**
     * 记录一次缓存旁路，并按区域增加 bypass 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void bypass(String region) {
        bypasses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "bypass");
    }

    /**
     * 记录一次等待缓存加载锁，并按区域增加 lock_wait 指标。
     *
     * @param region 业务缓存区域名称
     */
    public void lockWait(String region) {
        lockWaits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "lock_wait");
    }

    /**
     * 读取各 LongAdder 的当前累计值；各项分别取样。
     *
     * @return 包含七类本地缓存计数的快照
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

    /** 清零本地累计计数；已注册的 Micrometer 计数不在此重置。 */
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
     * 保存本地缓存命中、加载、错误、旁路和锁等待累计次数。
     *
     * @param hits 正向缓存命中次数
     * @param misses 缓存未命中次数
     * @param negativeHits 空值缓存命中次数
     * @param loads 数据源加载次数
     * @param errors 缓存操作错误次数
     * @param bypasses 缓存旁路次数
     * @param lockWaits 缓存加载锁等待次数
     */
    public record Snapshot(
            long hits, long misses, long negativeHits, long loads, long errors, long bypasses, long lockWaits) {
        /**
         * 以正向命中数除以正向命中与未命中的总数，空值命中不计入该比率。
         *
         * @return 没有命中或未命中记录时为 0，否则为正向命中比率
         */
        public double positiveHitRatio() {
            long attempts = hits + misses;
            return attempts == 0 ? 0.0 : (double) hits / attempts;
        }
    }
}
