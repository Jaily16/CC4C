package com.cc4c.shared;

import java.util.concurrent.atomic.LongAdder;

public final class BusinessCacheMetrics {
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder negativeHits = new LongAdder();
    private final LongAdder loads = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder bypasses = new LongAdder();
    private final LongAdder lockWaits = new LongAdder();

    void hit() {
        hits.increment();
    }

    void miss() {
        misses.increment();
    }

    void negativeHit() {
        negativeHits.increment();
    }

    void load() {
        loads.increment();
    }

    void error() {
        errors.increment();
    }

    void bypass() {
        bypasses.increment();
    }

    void lockWait() {
        lockWaits.increment();
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
