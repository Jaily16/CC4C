package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提供带代际失效、负缓存和故障旁路的业务缓存协调入口。
 */
@Component
public final class BusinessCache {
    private static final Logger logger = LoggerFactory.getLogger(BusinessCache.class);
    private static final Duration FAILURE_BYPASS = Duration.ofSeconds(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);
    private static final Duration LOCK_WAIT = Duration.ofMillis(200);

    private final ObjectMapper objectMapper;
    private final BusinessCacheProperties properties;
    private final BusinessCacheStore store;
    private final BusinessCacheMetrics metrics;
    private final BusinessCacheKeyFactory keyFactory;
    private final BusinessCacheValueCodec valueCodec;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<?>>> inFlight = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long bypassUntilNanos;

    /**
     * 创建 BusinessCache 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param properties 调用方提供的 {@code properties} 值
     * @param storeProvider 调用方提供的 {@code storeProvider} 值
     * @param micrometer 调用方提供的 {@code micrometer} 值
     */
    @Autowired
    public BusinessCache(
            ObjectMapper objectMapper,
            BusinessCacheProperties properties,
            ObjectProvider<BusinessCacheStore> storeProvider,
            Cc4cMetrics micrometer) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.store = storeProvider.getIfAvailable();
        this.metrics = new BusinessCacheMetrics(micrometer);
        this.keyFactory = new BusinessCacheKeyFactory(properties.namespace());
        this.valueCodec = new BusinessCacheValueCodec(objectMapper);
    }

    /**
     * 创建 BusinessCache 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param properties 调用方提供的 {@code properties} 值
     * @param storeProvider 调用方提供的 {@code storeProvider} 值
     */
    public BusinessCache(
            ObjectMapper objectMapper,
            BusinessCacheProperties properties,
            ObjectProvider<BusinessCacheStore> storeProvider) {
        this(objectMapper, properties, storeProvider, Cc4cMetrics.disabled());
    }

    /**
     * 查询并返回 BusinessCache 中与 getOrLoad 对应的数据，不改变业务状态。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param region 调用方提供的 {@code region} 值
     * @param logicalKey 调用方提供的 {@code logicalKey} 值
     * @param type 调用方提供的 {@code type} 值
     * @param ttl 调用方提供的 {@code ttl} 值
     * @param negativeTtl 调用方提供的 {@code negativeTtl} 值
     * @param loader 调用方提供的 {@code loader} 值
     * @return 存在时包含目标值，否则为空
     */
    public <T> Optional<T> getOrLoad(
            String region,
            String logicalKey,
            TypeReference<T> type,
            Duration ttl,
            Duration negativeTtl,
            Supplier<Optional<T>> loader) {
        if (!properties.enabled() || store == null || circuitOpen()) {
            metrics.bypass(region);
            return load(loader, region);
        }

        ResolvedKey key = resolveKey(region, logicalKey);
        if (key == null) {
            metrics.bypass(region);
            return load(loader, region);
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        ReadResult<T> first = read(key.dataKey(), javaType, region);
        if (first.state() == ReadState.VALUE) {
            metrics.hit(region);
            return Optional.of(first.value());
        }
        if (first.state() == ReadState.NEGATIVE) {
            metrics.negativeHit(region);
            return Optional.empty();
        }
        metrics.miss(region);
        return singleFlight(key, region, javaType, ttl, negativeTtl, loader);
    }

    /**
     * 执行 BusinessCache 中的 invalidateAfterCommit 职责，并保持既有权限、事务与副作用边界。
     *
     * @param regions 调用方提供的 {@code regions} 值
     */
    public void invalidateAfterCommit(String... regions) {
        Runnable invalidation = () -> {
            for (String region : regions) {
                invalidateNow(region);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 执行 BusinessCache 中的 afterCommit 职责，并保持既有权限、事务与副作用边界。
                 */
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
        } else {
            invalidation.run();
        }
    }

    /**
     * 执行 BusinessCache 中的 metrics 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public BusinessCacheMetrics metrics() {
        return metrics;
    }

    /**
     * 执行 BusinessCache 中的 healthSnapshot 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public HealthSnapshot healthSnapshot() {
        if (!properties.enabled()) {
            return new HealthSnapshot(false, true, false);
        }
        if (store == null) {
            return new HealthSnapshot(true, false, circuitOpen());
        }
        try {
            boolean reachable = store.ping();
            if (reachable) {
                markSuccess();
            }
            return new HealthSnapshot(true, reachable, circuitOpen());
        } catch (RuntimeException exception) {
            markFailure("health", "health", exception);
            return new HealthSnapshot(true, false, circuitOpen());
        }
    }

    /**
     * 执行 BusinessCache 中的 singleFlight 职责，并保持既有权限、事务与副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param key 调用方提供的 {@code key} 值
     * @param region 调用方提供的 {@code region} 值
     * @param javaType 调用方提供的 {@code javaType} 值
     * @param ttl 调用方提供的 {@code ttl} 值
     * @param negativeTtl 调用方提供的 {@code negativeTtl} 值
     * @param loader 调用方提供的 {@code loader} 值
     * @return 存在时包含目标值，否则为空
     */
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
            metrics.lockWait(region);
            @SuppressWarnings("unchecked")
            Optional<T> joined = (Optional<T>) existing.join();
            return joined;
        }

        try {
            Optional<T> loaded = loadWithDistributedLock(key, region, javaType, ttl, negativeTtl, loader);
            mine.complete(loaded);
            return loaded;
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key.dataKey(), mine);
        }
    }

    /**
     * 执行 BusinessCache 中的 loadWithDistributedLock 职责，并保持既有权限、事务与副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param key 调用方提供的 {@code key} 值
     * @param region 调用方提供的 {@code region} 值
     * @param javaType 调用方提供的 {@code javaType} 值
     * @param ttl 调用方提供的 {@code ttl} 值
     * @param negativeTtl 调用方提供的 {@code negativeTtl} 值
     * @param loader 调用方提供的 {@code loader} 值
     * @return 存在时包含目标值，否则为空
     */
    private <T> Optional<T> loadWithDistributedLock(
            ResolvedKey key,
            String region,
            JavaType javaType,
            Duration ttl,
            Duration negativeTtl,
            Supplier<Optional<T>> loader) {
        String lockKey = keyFactory.lockKey(key.dataKey());
        String token = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = store.setIfAbsent(lockKey, token, LOCK_TTL);
            markSuccess();
        } catch (RuntimeException exception) {
            markFailure("lock", region, exception);
            metrics.bypass(region);
            return load(loader, region);
        }

        if (locked) {
            try {
                ReadResult<T> second = read(key.dataKey(), javaType, region);
                if (second.state() == ReadState.VALUE) {
                    metrics.hit(region);
                    return Optional.of(second.value());
                }
                if (second.state() == ReadState.NEGATIVE) {
                    metrics.negativeHit(region);
                    return Optional.empty();
                }
                Optional<T> loaded = load(loader, region);
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

        metrics.lockWait(region);
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
                metrics.hit(region);
                return Optional.of(result.value());
            }
            if (result.state() == ReadState.NEGATIVE) {
                metrics.negativeHit(region);
                return Optional.empty();
            }
            if (result.state() == ReadState.ERROR) {
                break;
            }
        }
        metrics.bypass(region);
        return load(loader, region);
    }

    /**
     * 执行 BusinessCache 中的 resolveKey 职责，并保持既有权限、事务与副作用边界。
     *
     * @param region 调用方提供的 {@code region} 值
     * @param logicalKey 调用方提供的 {@code logicalKey} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private ResolvedKey resolveKey(String region, String logicalKey) {
        String generationKey = keyFactory.generationKey(region);
        try {
            String generation = store.get(generationKey);
            markSuccess();
            if (generation == null) {
                generation = "0";
            }
            return new ResolvedKey(keyFactory.dataKey(region, generation, logicalKey));
        } catch (RuntimeException exception) {
            markFailure("generation-read", region, exception);
            return null;
        }
    }

    /**
     * 执行 BusinessCache 中的 invalidateNow 职责，并保持既有权限、事务与副作用边界。
     *
     * @param region 调用方提供的 {@code region} 值
     */
    private void invalidateNow(String region) {
        if (!properties.enabled() || store == null || circuitOpen()) {
            return;
        }
        String generationKey = keyFactory.generationKey(region);
        try {
            store.increment(generationKey);
            markSuccess();
        } catch (RuntimeException exception) {
            markFailure("invalidate", region, exception);
        }
    }

    /**
     * 执行 BusinessCache 中的 read 职责，并保持既有权限、事务与副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param key 调用方提供的 {@code key} 值
     * @param javaType 调用方提供的 {@code javaType} 值
     * @param region 调用方提供的 {@code region} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
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
            BusinessCacheValueCodec.DecodedValue<T> decoded = valueCodec.decode(json, javaType);
            if (decoded.state() == BusinessCacheValueCodec.DecodedValue.State.NEGATIVE) {
                return ReadResult.negative();
            }
            return ReadResult.value(decoded.value());
        } catch (Exception exception) {
            metrics.error(region);
            logger.atWarn()
                    .addKeyValue("event", "business_cache_invalid_entry")
                    .addKeyValue("region", region)
                    .addKeyValue("exception_type", exception.getClass().getSimpleName())
                    .log("Invalid business cache entry was discarded");
            try {
                store.delete(key);
                markSuccess();
            } catch (RuntimeException deleteFailure) {
                markFailure("delete-invalid", region, deleteFailure);
            }
            return ReadResult.miss();
        }
    }

    /**
     * 执行 BusinessCache 中的 write 职责，并保持既有权限、事务与副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param key 调用方提供的 {@code key} 值
     * @param value 调用方提供的 {@code value} 值
     * @param ttl 调用方提供的 {@code ttl} 值
     * @param region 调用方提供的 {@code region} 值
     */
    private <T> void write(String key, Optional<T> value, Duration ttl, String region) {
        try {
            String json = valueCodec.encode(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > BusinessCacheValueCodec.MAX_ENTRY_BYTES) {
                return;
            }
            store.set(key, json, jitter(ttl));
            markSuccess();
        } catch (JsonProcessingException exception) {
            metrics.error(region);
            logger.atWarn()
                    .addKeyValue("event", "business_cache_serialization_failure")
                    .addKeyValue("region", region)
                    .addKeyValue("exception_type", exception.getClass().getSimpleName())
                    .log("Business cache value could not be serialized");
        } catch (RuntimeException exception) {
            markFailure("write", region, exception);
        }
    }

    /**
     * 执行 BusinessCache 中的 load 职责，并保持既有权限、事务与副作用边界。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param loader 调用方提供的 {@code loader} 值
     * @param region 调用方提供的 {@code region} 值
     * @return 存在时包含目标值，否则为空
     */
    private <T> Optional<T> load(Supplier<Optional<T>> loader, String region) {
        long startedNanos = System.nanoTime();
        try {
            Optional<T> loaded = loader.get();
            if (loaded == null) {
                throw new IllegalStateException("Business cache loader returned null Optional");
            }
            metrics.load(region, startedNanos, "success");
            return loaded;
        } catch (RuntimeException exception) {
            metrics.load(region, startedNanos, "error");
            throw exception;
        }
    }

    /**
     * 执行 BusinessCache 中的 jitter 职责，并保持既有权限、事务与副作用边界。
     *
     * @param ttl 调用方提供的 {@code ttl} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private Duration jitter(Duration ttl) {
        double factor = ThreadLocalRandom.current().nextDouble(0.85, 1.1500001);
        return Duration.ofMillis(Math.max(1, Math.round(ttl.toMillis() * factor)));
    }

    /**
     * 执行 BusinessCache 中的 circuitOpen 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 当前条件是否成立
     */
    private boolean circuitOpen() {
        return System.nanoTime() < bypassUntilNanos;
    }

    /**
     * 执行 BusinessCache 中的 markSuccess 职责，并保持既有权限、事务与副作用边界。
     */
    private void markSuccess() {
        consecutiveFailures.set(0);
        if (System.nanoTime() >= bypassUntilNanos) {
            bypassUntilNanos = 0;
        }
    }

    /**
     * 执行 BusinessCache 中的 markFailure 职责，并保持既有权限、事务与副作用边界。
     *
     * @param operation 调用方提供的 {@code operation} 值
     * @param region 调用方提供的 {@code region} 值
     * @param exception 调用方提供的 {@code exception} 值
     */
    private void markFailure(String operation, String region, RuntimeException exception) {
        metrics.error(region);
        if (consecutiveFailures.incrementAndGet() >= 3) {
            bypassUntilNanos = System.nanoTime() + FAILURE_BYPASS.toNanos();
        }
        logger.atWarn()
                .addKeyValue("event", "business_cache_failure")
                .addKeyValue("operation", operation)
                .addKeyValue("region", region)
                .addKeyValue("exception_type", exception.getClass().getSimpleName())
                .log("Business cache operation failed");
    }

    /**
     * ResolvedKey 以不可变字段承载共享基础设施数据并保持既有协议语义。
     *
     * @param dataKey 调用方提供的 {@code dataKey} 值
     */
    private record ResolvedKey(String dataKey) {}

    /**
     * ReadState 枚举共享基础设施允许出现的有限状态或协议取值。
     */
    private enum ReadState {
        VALUE,
        NEGATIVE,
        MISS,
        ERROR
    }

    /**
     * ReadResult 以不可变字段承载共享基础设施数据并保持既有协议语义。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param state 调用方提供的 {@code state} 值
     * @param value 调用方提供的 {@code value} 值
     */
    private record ReadResult<T>(ReadState state, T value) {
        /**
         * 执行 ReadResult 中的 value 职责，并保持既有权限、事务与副作用边界。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @param value 调用方提供的 {@code value} 值
         * @return 按当前声明计算、查询或转换得到的结果
         */
        static <T> ReadResult<T> value(T value) {
            return new ReadResult<>(ReadState.VALUE, value);
        }

        /**
         * 执行 ReadResult 中的 negative 职责，并保持既有权限、事务与副作用边界。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @return 按当前声明计算、查询或转换得到的结果
         */
        static <T> ReadResult<T> negative() {
            return new ReadResult<>(ReadState.NEGATIVE, null);
        }

        /**
         * 执行 ReadResult 中的 miss 职责，并保持既有权限、事务与副作用边界。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @return 按当前声明计算、查询或转换得到的结果
         */
        static <T> ReadResult<T> miss() {
            return new ReadResult<>(ReadState.MISS, null);
        }

        /**
         * 执行 ReadResult 中的 error 职责，并保持既有权限、事务与副作用边界。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @return 按当前声明计算、查询或转换得到的结果
         */
        static <T> ReadResult<T> error() {
            return new ReadResult<>(ReadState.ERROR, null);
        }
    }

    /**
     * 描述缓存开关、后端连通性和故障旁路状态。
     *
     * @param enabled 是否启用对应受控能力
     * @param reachable 调用方提供的 {@code reachable} 值
     * @param bypassing 调用方提供的 {@code bypassing} 值
     */
    public record HealthSnapshot(boolean enabled, boolean reachable, boolean bypassing) {}
}
