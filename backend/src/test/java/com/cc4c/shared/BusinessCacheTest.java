package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BusinessCacheTest {
    private final FakeStore store = new FakeStore();
    private final BusinessCache cache = cache(store);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void loadsOnceThenReturnsTypedJsonHitWithJitteredTtl() {
        AtomicInteger loads = new AtomicInteger();

        Optional<Value> first = cache.getOrLoad(
                "catalog:detail",
                "course-a",
                new TypeReference<>() {},
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                () -> Optional.of(new Value(loads.incrementAndGet(), "course-a")));
        Optional<Value> second = cache.getOrLoad(
                "catalog:detail",
                "course-a",
                new TypeReference<>() {},
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                () -> Optional.of(new Value(loads.incrementAndGet(), "course-a")));

        assertEquals(first, second);
        assertEquals(1, loads.get());
        assertTrue(store.lastTtl.toMillis() >= 51_000);
        assertTrue(store.lastTtl.toMillis() <= 69_000);
        assertEquals(1, cache.metrics().snapshot().hits());
    }

    @Test
    void cachesNegativeLookupWithShortTtl() {
        AtomicInteger loads = new AtomicInteger();

        Optional<Value> first = lookupMissing(loads);
        Optional<Value> second = lookupMissing(loads);

        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        assertEquals(1, loads.get());
        assertTrue(store.lastTtl.toMillis() >= 25_500);
        assertTrue(store.lastTtl.toMillis() <= 34_500);
        assertEquals(1, cache.metrics().snapshot().negativeHits());
    }

    @Test
    void concurrentMissesUseOneLocalLoader() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        store.coordinateDataReads(8);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Optional<Value>>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> cache.getOrLoad(
                        "community:detail",
                        "42",
                        new TypeReference<>() {},
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(30),
                        () -> {
                            loads.incrementAndGet();
                            loaderStarted.countDown();
                            await(releaseLoader);
                            return Optional.of(new Value(42, "blog"));
                        })));
            }
            assertTrue(loaderStarted.await(2, TimeUnit.SECONDS));
            releaseLoader.countDown();
            for (var future : futures) {
                assertEquals(42, future.get(2, TimeUnit.SECONDS).orElseThrow().id());
            }
        }
        assertEquals(1, loads.get());
        assertEquals(8, cache.metrics().snapshot().misses());
    }

    @Test
    void threeRedisFailuresOpenFailOpenBypass() {
        store.failReads = true;
        AtomicInteger loads = new AtomicInteger();

        for (int index = 0; index < 4; index++) {
            Optional<Value> value = cache.getOrLoad(
                    "catalog:home",
                    "1:20",
                    new TypeReference<>() {},
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(30),
                    () -> Optional.of(new Value(loads.incrementAndGet(), "db")));
            assertTrue(value.isPresent());
        }

        assertEquals(4, loads.get());
        assertEquals(3, store.readFailures.get());
        assertTrue(cache.metrics().snapshot().bypasses() >= 4);
    }

    @Test
    void invalidationRunsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        cache.invalidateAfterCommit("catalog:home", "catalog:detail");
        assertEquals(0, store.increments.get());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertEquals(2, store.increments.get());
    }

    @Test
    void invalidJsonIsDeletedAndReloaded() {
        cache.getOrLoad(
                "catalog:detail",
                "broken",
                new TypeReference<Value>() {},
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                () -> Optional.of(new Value(1, "first")));
        String dataKey = store.values.keySet().stream()
                .filter(key -> !key.endsWith(":generation") && !key.endsWith(":lock"))
                .findFirst()
                .orElseThrow();
        store.values.put(dataKey, "{broken-json");

        Optional<Value> result = cache.getOrLoad(
                "catalog:detail",
                "broken",
                new TypeReference<>() {},
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                () -> Optional.of(new Value(2, "reloaded")));

        assertEquals(2, result.orElseThrow().id());
        assertTrue(store.deletedKeys.contains(dataKey));
        assertTrue(cache.metrics().snapshot().errors() >= 1);
    }

    @Test
    void testCleanupUsesOnlyConfiguredNamespacePrefix() {
        store.values.put("cc4c:test:cache:v1:a", "1");
        store.values.put("cc4c:session:v1:a", "2");

        assertEquals(1, cache.clearNamespaceForTests());
        assertFalse(store.values.containsKey("cc4c:test:cache:v1:a"));
        assertTrue(store.values.containsKey("cc4c:session:v1:a"));
    }

    private Optional<Value> lookupMissing(AtomicInteger loads) {
        return cache.getOrLoad(
                "catalog:detail",
                "missing",
                new TypeReference<>() {},
                Duration.ofSeconds(60),
                Duration.ofSeconds(30),
                () -> {
                    loads.incrementAndGet();
                    return Optional.empty();
                });
    }

    private BusinessCache cache(BusinessCacheStore valueStore) {
        @SuppressWarnings("unchecked")
        ObjectProvider<BusinessCacheStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(valueStore);
        return new BusinessCache(
                new ObjectMapper().findAndRegisterModules(),
                new BusinessCacheProperties(true, "redis://unused", "cc4c:test:cache", true),
                provider);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Value(int id, String name) {}

    private static final class FakeStore implements BusinessCacheStore {
        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private final AtomicInteger increments = new AtomicInteger();
        private final AtomicInteger readFailures = new AtomicInteger();
        private volatile CountDownLatch coordinatedDataReads;
        private volatile boolean failReads;
        private volatile Duration lastTtl = Duration.ZERO;

        private void coordinateDataReads(int expectedReads) {
            coordinatedDataReads = new CountDownLatch(expectedReads);
        }

        @Override
        public String get(String key) {
            String currentValue = values.get(key);
            CountDownLatch readGate = coordinatedDataReads;
            if (readGate != null && !key.endsWith(":generation") && !key.endsWith(":lock")) {
                readGate.countDown();
                try {
                    if (!readGate.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent cache reads");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            if (failReads) {
                readFailures.incrementAndGet();
                throw new IllegalStateException("Redis unavailable");
            }
            return currentValue;
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
            lastTtl = ttl;
        }

        @Override
        public boolean setIfAbsent(String key, String value, Duration ttl) {
            return values.putIfAbsent(key, value) == null;
        }

        @Override
        public long increment(String key) {
            int value = increments.incrementAndGet();
            values.put(key, Integer.toString(value));
            return value;
        }

        @Override
        public void delete(String key) {
            values.remove(key);
            deletedKeys.add(key);
        }

        @Override
        public boolean compareAndDelete(String key, String expectedValue) {
            return values.remove(key, expectedValue);
        }

        @Override
        public long deleteByPrefix(String prefix) {
            List<String> keys = values.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .toList();
            keys.forEach(values::remove);
            return keys.size();
        }
    }
}
