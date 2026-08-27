package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public final class BusinessCache {
    private static final Logger logger = LoggerFactory.getLogger(BusinessCache.class);
    private static final int ENVELOPE_VERSION = 1;
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;
    private static final Duration FAILURE_BYPASS = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final Duration LOCK_WAIT = Duration.ofMillis(200);

    private final ObjectMapper objectMapper;
    private final BusinessCacheProperties properties;
    private final BusinessCacheStore store;
    private final BusinessCacheMetrics metrics = new BusinessCacheMetrics();
    private final ConcurrentHashMap<String, CompletableFuture<Optional<?>>> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long bypassUntilNanos;

    public BusinessCache(
            ObjectMapper objectMapper,
            BusinessCacheProperties properties,
            ObjectProvider<BusinessCacheStore> storeProvider) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.store = storeProvider.getIfAvailable();
    }

    public <T> Optional<T> getOrLoad(
            String region,
            String logicalKey,
            TypeReference<T> type,
            Duration ttl,
            Duration negativeTtl,
            Supplier<Optional<T>> loader) {
        if (!properties.enabled() || store == null || circuitOpen()) {
            metrics.bypass();
            return load(loader);
        }

        ResolvedKey key = resolveKey(region, logicalKey);
        if (key == null) {
            metrics.bypass();
            return load(loader);
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        ReadResult<T> first = read(key.dataKey(), javaType, region);
        if (first.state() == ReadState.VALUE) {
            metrics.hit();
            return Optional.of(first.value());
        }
        if (first.state() == ReadState.NEGATIVE) {
            metrics.negativeHit();
            return Optional.empty();
        }
        metrics.miss();
        return singleFlight(key, region, javaType, ttl, negativeTtl, loader);
    }

    public void invalidateAfterCommit(String... regions) {
        Runnable invalidation = () -> {
            for (String region : regions) {
                invalidateNow(region);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
        } else {
            invalidation.run();
        }
    }

    public BusinessCacheMetrics metrics() {
        return metrics;
    }

    public long clearNamespaceForTests() {
        if (!properties.testCleanupEnabled()) {
            throw new IllegalStateException("Business cache namespace cleanup is disabled");
        }
        if (store == null) {
            return 0;
        }
        return store.deleteByPrefix(properties.namespace() + ":");
    }

    private <T> Optional<T> singleFlight(
            ResolvedKey key,
            String region,
            JavaType javaType,
            Duration ttl,
            Duration negativeTtl,
            Supplier<Optional<T>> loader) {
        CompletableFuture<Optional<?>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<?>> existing = inFlight.putIfAbsent(key.dataKey(), mine);
        if (existing != null) {
            metrics.lockWait();
            @SuppressWarnings("unchecked")
            Optional<T> joined = (Optional<T>) existing.join();
            return joined;
        }

        try {
            Optional<T> loaded = loadWithDistributedLock(
                    key, region, javaType, ttl, negativeTtl, loader);
            mine.complete(loaded);
            return loaded;
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key.dataKey(), mine);
        }
    }

    private <T> Optional<T> loadWithDistributedLock(
            ResolvedKey key,
            String region,
            JavaType javaType,
            Duration ttl,
            Duration negativeTtl,
            Supplier<Optional<T>> loader) {
        String lockKey = key.dataKey() + ":lock";
        String token = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = store.setIfAbsent(lockKey, token, LOCK_TTL);
            markSuccess();
        } catch (RuntimeException exception) {
            markFailure("lock", region, exception);
            metrics.bypass();
            return load(loader);
        }

        if (locked) {
            try {
                ReadResult<T> second = read(key.dataKey(), javaType, region);
                if (second.state() == ReadState.VALUE) {
                    metrics.hit();
                    return Optional.of(second.value());
                }
                if (second.state() == ReadState.NEGATIVE) {
                    metrics.negativeHit();
                    return Optional.empty();
                }
                Optional<T> loaded = load(loader);
                write(key.dataKey(), loaded, loaded.isPresent() ? ttl : negativeTtl, region);
                return loaded;
            } finally {
                try {
                    store.compareAndDelete(lockKey, token);
                    markSuccess();
                } catch (RuntimeException exception) {
                    markFailure("unlock", region, exception);
                }
            }
        }

        metrics.lockWait();
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(15, 31));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            ReadResult<T> result = read(key.dataKey(), javaType, region);
            if (result.state() == ReadState.VALUE) {
                metrics.hit();
                return Optional.of(result.value());
            }
            if (result.state() == ReadState.NEGATIVE) {
                metrics.negativeHit();
                return Optional.empty();
            }
            if (result.state() == ReadState.ERROR) {
                break;
            }
        }
        metrics.bypass();
        return load(loader);
    }

    private ResolvedKey resolveKey(String region, String logicalKey) {
        validateRegion(region);
        String generationKey = properties.namespace() + ":v1:" + region + ":generation";
        try {
            String generation = store.get(generationKey);
            markSuccess();
            if (generation == null) {
                generation = "0";
            }
            String dataKey = properties.namespace() + ":v1:" + region + ":g" + generation
                    + ":" + sha256(logicalKey);
            return new ResolvedKey(dataKey);
        } catch (RuntimeException exception) {
            markFailure("generation-read", region, exception);
            return null;
        }
    }

    private void invalidateNow(String region) {
        if (!properties.enabled() || store == null || circuitOpen()) {
            return;
        }
        validateRegion(region);
        String generationKey = properties.namespace() + ":v1:" + region + ":generation";
        try {
            store.increment(generationKey);
            markSuccess();
        } catch (RuntimeException exception) {
            markFailure("invalidate", region, exception);
        }
    }

    private <T> ReadResult<T> read(String key, JavaType javaType, String region) {
        String json;
        try {
            json = store.get(key);
            markSuccess();
        } catch (RuntimeException exception) {
            markFailure("read", region, exception);
            return ReadResult.error();
        }
        if (json == null) {
            return ReadResult.miss();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("schemaVersion").asInt(-1) != ENVELOPE_VERSION) {
                throw new JsonProcessingException("Unsupported cache envelope version") { };
            }
            if (root.path("negative").asBoolean(false)) {
                return ReadResult.negative();
            }
            JsonNode valueNode = root.get("value");
            if (valueNode == null || valueNode.isNull()) {
                throw new JsonProcessingException("Cache value is missing") { };
            }
            T value = objectMapper.readerFor(javaType).readValue(valueNode);
            return ReadResult.value(value);
        } catch (Exception exception) {
            metrics.error();
            logger.warn("business_cache_invalid_entry region={} type={}",
                    region, exception.getClass().getSimpleName());
            try {
                store.delete(key);
                markSuccess();
            } catch (RuntimeException deleteFailure) {
                markFailure("delete-invalid", region, deleteFailure);
            }
            return ReadResult.miss();
        }
    }

    private <T> void write(String key, Optional<T> value, Duration ttl, String region) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", ENVELOPE_VERSION);
            root.put("negative", value.isEmpty());
            if (value.isPresent()) {
                root.set("value", objectMapper.valueToTree(value.get()));
            } else {
                root.putNull("value");
            }
            String json = objectMapper.writeValueAsString(root);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ENTRY_BYTES) {
                return;
            }
            store.set(key, json, jitter(ttl));
            markSuccess();
        } catch (JsonProcessingException exception) {
            metrics.error();
            logger.warn("business_cache_serialization_failure region={} type={}",
                    region, exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            markFailure("write", region, exception);
        }
    }

    private <T> Optional<T> load(Supplier<Optional<T>> loader) {
        metrics.load();
        Optional<T> loaded = loader.get();
        if (loaded == null) {
            throw new IllegalStateException("Business cache loader returned null Optional");
        }
        return loaded;
    }

    private Duration jitter(Duration ttl) {
        double factor = ThreadLocalRandom.current().nextDouble(0.85, 1.1500001);
        return Duration.ofMillis(Math.max(1, Math.round(ttl.toMillis() * factor)));
    }

    private boolean circuitOpen() {
        return System.nanoTime() < bypassUntilNanos;
    }

    private void markSuccess() {
        consecutiveFailures.set(0);
        if (System.nanoTime() >= bypassUntilNanos) {
            bypassUntilNanos = 0;
        }
    }

    private void markFailure(String operation, String region, RuntimeException exception) {
        metrics.error();
        if (consecutiveFailures.incrementAndGet() >= 3) {
            bypassUntilNanos = System.nanoTime() + FAILURE_BYPASS.toNanos();
        }
        logger.warn("business_cache_failure operation={} region={} type={}",
                operation, region, exception.getClass().getSimpleName());
    }

    private void validateRegion(String region) {
        if (region == null || !region.matches("[a-z0-9:-]{2,80}")) {
            throw new IllegalArgumentException("Invalid business cache region");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ResolvedKey(String dataKey) {
    }

    private enum ReadState {
        VALUE,
        NEGATIVE,
        MISS,
        ERROR
    }

    private record ReadResult<T>(ReadState state, T value) {
        static <T> ReadResult<T> value(T value) {
            return new ReadResult<>(ReadState.VALUE, value);
        }

        static <T> ReadResult<T> negative() {
            return new ReadResult<>(ReadState.NEGATIVE, null);
        }

        static <T> ReadResult<T> miss() {
            return new ReadResult<>(ReadState.MISS, null);
        }

        static <T> ReadResult<T> error() {
            return new ReadResult<>(ReadState.ERROR, null);
        }
    }
}
