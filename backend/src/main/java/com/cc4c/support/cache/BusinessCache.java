package com.cc4c.support.cache;

import com.cc4c.config.BusinessCacheProperties;
import com.cc4c.support.monitoring.BusinessCacheMetrics;
import com.cc4c.support.monitoring.Cc4cMetrics;
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
     * 接入可选缓存存储并创建指标、键工厂及信封编解码器。
     *
     * @param objectMapper 应用 JSON 映射器
     * @param properties 缓存开关及命名空间配置
     * @param storeProvider 按配置可选提供的缓存存储
     * @param micrometer 底层统一指标记录器
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
     * 委托主构造器，使用禁用指标实现。
     *
     * @param objectMapper 应用 JSON 映射器
     * @param properties 缓存开关及命名空间配置
     * @param storeProvider 按配置可选提供的缓存存储
     */
    public BusinessCache(
            ObjectMapper objectMapper,
            BusinessCacheProperties properties,
            ObjectProvider<BusinessCacheStore> storeProvider) {
        this(objectMapper, properties, storeProvider, Cc4cMetrics.disabled());
    }

    /**
     * 按分区代次读取显式类型缓存，区分值与负缓存；禁用、熔断或代次读取失败时直接调用加载器。
     *
     * @param <T> 缓存值的数据类型
     * @param region 已校验格式的缓存分区
     * @param logicalKey 分区内的原始逻辑查询键
     * @param type 保留泛型信息的目标缓存类型
     * @param ttl 本次写入的基础有效期
     * @param negativeTtl 业务对象不存在时的负缓存有效期
     * @param loader 返回非空 Optional 的业务加载器
     * @return 缓存命中或加载得到的值，空 Optional 表示业务对象不存在
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
     * 事务同步及实际事务均启用时登记提交后回调，否则立即逐分区失效。
     *
     * @param regions 需要推进代次的缓存分区
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
                /** 事务提交成功后依次执行本次登记的分区失效。 */
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
     * 返回当前缓存实例使用的统计记录器。
     *
     * @return 缓存命中、加载及错误指标入口
     */
    public BusinessCacheMetrics metrics() {
        return metrics;
    }

    /**
     * 缓存启用时执行存储 PING 并更新成功或失败计数；禁用时不访问存储。
     *
     * @return 缓存开关、连通性和旁路状态
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
     * 合并进程内同一数据键的并发加载，共享结果或异常，并在结束时移除本次任务。
     *
     * @param <T> 缓存值的数据类型
     * @param key 含分区代次的数据键封装
     * @param region 已校验格式的缓存分区
     * @param javaType 明确指定的解码目标类型
     * @param ttl 本次写入的基础有效期
     * @param negativeTtl 业务对象不存在时的负缓存有效期
     * @param loader 返回非空 Optional 的业务加载器
     * @return 当前加载或已在执行任务的结果
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
     * 尝试获取三秒锁并二次读取，持锁者加载及回填后按令牌解锁；未获锁最多等 200 毫秒后旁路加载，不回填。
     *
     * @param <T> 缓存值的数据类型
     * @param key 含分区代次的数据键封装
     * @param region 已校验格式的缓存分区
     * @param javaType 明确指定的解码目标类型
     * @param ttl 本次写入的基础有效期
     * @param negativeTtl 业务对象不存在时的负缓存有效期
     * @param loader 返回非空 Optional 的业务加载器
     * @return 缓存或加载器提供的业务结果
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
     * 读取分区当前代次，缺失代次按 0 处理；存储失败记录错误并返回空。
     *
     * @param region 已校验格式的缓存分区
     * @param logicalKey 分区内的原始逻辑查询键
     * @return 带代次的数据键，无法解析存储代次时为空
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
     * 缓存可用时递增分区代次，使旧代次键不再被读取；异常记录后吞掉，不扫描删除旧数据。
     *
     * @param region 已校验格式的缓存分区
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
     * 读取并解码缓存信封；存储错误返回 ERROR，无效信封尝试删除后按 MISS 处理。
     *
     * @param <T> 缓存值的数据类型
     * @param key 完整缓存数据键
     * @param javaType 明确指定的解码目标类型
     * @param region 已校验格式的缓存分区
     * @return 普通值、负缓存、未命中或存储错误
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
     * 编码值或负缓存，超过 1 MiB 时不写入；写入使用抖动 TTL，编码和存储异常只记录不外抛。
     *
     * @param <T> 缓存值的数据类型
     * @param key 完整缓存数据键
     * @param value 业务结果；空 Optional 表示对象不存在
     * @param ttl 本次写入的基础有效期
     * @param region 已校验格式的缓存分区
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
     * 调用业务加载器并记录耗时及结果；禁止返回 null Optional，业务运行异常继续传播。
     *
     * @param <T> 缓存值的数据类型
     * @param loader 返回非空 Optional 的业务加载器
     * @param region 已校验格式的缓存分区
     * @return 加载器返回的非空 Optional
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
     * 将基础 TTL 乘以约 0.85 至 1.15 的随机因子，最短保留一毫秒。
     *
     * @param ttl 本次写入的基础有效期
     * @return 加入抖动后的有效期
     */
    private Duration jitter(Duration ttl) {
        double factor = ThreadLocalRandom.current().nextDouble(0.85, 1.1500001);
        return Duration.ofMillis(Math.max(1, Math.round(ttl.toMillis() * factor)));
    }

    /**
     * 以单调时钟判断当前是否仍处于故障旁路窗口。
     *
     * @return 旁路截止时间尚未到达时为 true
     */
    private boolean circuitOpen() {
        return System.nanoTime() < bypassUntilNanos;
    }

    /** 清零连续失败计数；只有旁路期限已结束时才清除旁路截止标记。 */
    private void markSuccess() {
        consecutiveFailures.set(0);
        if (System.nanoTime() >= bypassUntilNanos) {
            bypassUntilNanos = 0;
        }
    }

    /**
     * 累计连续缓存失败，达到三次后设置三十秒旁路；日志只记录操作、分区和异常类型。
     *
     * @param operation 低基数缓存操作分类
     * @param region 已校验格式的缓存分区
     * @param exception 本次缓存运行异常，仅记录其类型
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
     * 保存已包含命名空间、代次及逻辑摘要的数据键。
     *
     * @param dataKey 已解析的完整数据键
     */
    private record ResolvedKey(String dataKey) {}

    /** 区分普通命中、负缓存命中、未命中和存储故障。 */
    private enum ReadState {
        VALUE,
        NEGATIVE,
        MISS,
        ERROR
    }

    /**
     * 携带读取状态及可选值，避免把缓存故障或负缓存混同为普通值。
     *
     * @param <T> 缓存值的数据类型
     * @param state 读取或解码状态
     * @param value 待封装或存储的缓存值
     */
    private record ReadResult<T>(ReadState state, T value) {
        /**
         * 创建普通缓存命中结果。
         *
         * @param <T> 缓存值的数据类型
         * @param value 待封装或存储的缓存值
         * @return 带 VALUE 状态的读取结果
         */
        static <T> ReadResult<T> value(T value) {
            return new ReadResult<>(ReadState.VALUE, value);
        }

        /**
         * 创建业务对象不存在的负缓存命中结果。
         *
         * @param <T> 缓存值的数据类型
         * @return 带 NEGATIVE 状态且值为空的结果
         */
        static <T> ReadResult<T> negative() {
            return new ReadResult<>(ReadState.NEGATIVE, null);
        }

        /**
         * 创建缓存未命中结果。
         *
         * @param <T> 缓存值的数据类型
         * @return 带 MISS 状态且值为空的结果
         */
        static <T> ReadResult<T> miss() {
            return new ReadResult<>(ReadState.MISS, null);
        }

        /**
         * 创建存储读取失败结果。
         *
         * @param <T> 缓存值的数据类型
         * @return 带 ERROR 状态且值为空的结果
         */
        static <T> ReadResult<T> error() {
            return new ReadResult<>(ReadState.ERROR, null);
        }
    }

    /**
     * 描述缓存开关、后端连通性和故障旁路状态。
     *
     * @param enabled 是否启用业务缓存
     * @param reachable 缓存存储是否可达；禁用缓存时为 true
     * @param bypassing 是否处于故障旁路窗口
     */
    public record HealthSnapshot(boolean enabled, boolean reachable, boolean bypassing) {}
}
