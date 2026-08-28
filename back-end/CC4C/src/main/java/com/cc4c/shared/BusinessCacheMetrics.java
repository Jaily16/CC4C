package com.cc4c.shared;

import java.util.concurrent.atomic.LongAdder;

public final class BusinessCacheMetrics {
    private final Cc4cMetrics micrometer;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder negativeHits = new LongAdder();
    private final LongAdder loads = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder bypasses = new LongAdder();
    private final LongAdder lockWaits = new LongAdder();

    public BusinessCacheMetrics() {
        this(Cc4cMetrics.disabled());
    }

    BusinessCacheMetrics(Cc4cMetrics micrometer) {
        this.micrometer = micrometer;
    }

    void hit(String region) {
        hits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "hit");
    }

    void miss(String region) {
        misses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "miss");
    }

    void negativeHit(String region) {
        negativeHits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "negative");
    }

    void load(String region, long startedNanos, String outcome) {
        loads.increment();
        micrometer.record("cc4c.cache.load.duration", startedNanos,
                "region", region, "outcome", outcome);
    }

    void error(String region) {
        errors.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "error");
    }

    void bypass(String region) {
        bypasses.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "bypass");
    }

    void lockWait(String region) {
        lockWaits.increment();
        micrometer.increment("cc4c.cache.requests", "region", region, "outcome", "lock_wait");
    }

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

    public void reset() {
        hits.reset();
        misses.reset();
        negativeHits.reset();
        loads.reset();
        errors.reset();
        bypasses.reset();
        lockWaits.reset();
    }

    public record Snapshot(
            long hits,
            long misses,
            long negativeHits,
            long loads,
            long errors,
            long bypasses,
            long lockWaits
    ) {
        public double positiveHitRatio() {
            long attempts = hits + misses;
            return attempts == 0 ? 0.0 : (double) hits / attempts;
        }
    }
}
