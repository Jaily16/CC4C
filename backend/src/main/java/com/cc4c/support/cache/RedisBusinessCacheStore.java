package com.cc4c.support.cache;

import com.cc4c.support.monitoring.Cc4cMetrics;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 定义或实现共享基础设施状态的基础设施存取边界。
 */
public final class RedisBusinessCacheStore implements BusinessCacheStore, InitializingBean, DisposableBean {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final String redisUrl;
    private final Cc4cMetrics metrics;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;

    /**
     * 创建 RedisBusinessCacheStore 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redisUrl 调用方提供的 {@code redisUrl} 值
     */
    RedisBusinessCacheStore(String redisUrl) {
        this(redisUrl, Cc4cMetrics.disabled());
    }

    /**
     * 创建 RedisBusinessCacheStore 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redisUrl 调用方提供的 {@code redisUrl} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    public RedisBusinessCacheStore(String redisUrl, Cc4cMetrics metrics) {
        this.redisUrl = redisUrl;
        this.metrics = metrics;
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     */
    @Override
    public void afterPropertiesSet() {
        URI uri = URI.create(redisUrl);
        if (!"redis".equalsIgnoreCase(uri.getScheme()) && !"rediss".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("CC4C cache Redis URL must use redis:// or rediss://");
        }
        if (uri.getHost() == null) {
            throw new IllegalStateException("CC4C cache Redis URL must contain a host");
        }

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(uri.getHost());
        standalone.setPort(uri.getPort() < 0 ? 6379 : uri.getPort());
        configureCredentials(uri, standalone);
        configureDatabase(uri, standalone);

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder()
                .commandTimeout(IO_TIMEOUT)
                .shutdownTimeout(Duration.ofMillis(100))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(IO_TIMEOUT)
                                .build())
                        .build());
        if ("rediss".equalsIgnoreCase(uri.getScheme())) {
            client.useSsl();
        }

        connectionFactory = new LettuceConnectionFactory(standalone, client.build());
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    /**
     * 读取业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @return 按当前协议生成或读取的字符串值
     */
    @Override
    public String get(String key) {
        return observe("get", () -> template.opsForValue().get(key));
    }

    /**
     * 更新业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param value 待处理或存储的值
     * @param ttl 正向值的有效期
     */
    @Override
    public void set(String key, String value, Duration ttl) {
        observe("set", () -> {
            template.opsForValue().set(key, value, ttl);
            return null;
        });
    }

    /**
     * 更新业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param value 待处理或存储的值
     * @param ttl 正向值的有效期
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return observe(
                "set_if_absent",
                () -> Boolean.TRUE.equals(template.opsForValue().setIfAbsent(key, value, ttl)));
    }

    /**
     * 记录业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @return 按当前规则计算或读取的数值
     */
    @Override
    public long increment(String key) {
        Long result = observe("increment", () -> template.opsForValue().increment(key));
        if (result == null) {
            throw new IllegalStateException("Redis increment returned no result");
        }
        return result;
    }

    /**
     * 删除或失效业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     */
    @Override
    public void delete(String key) {
        observe("delete", () -> {
            template.delete(key);
            return null;
        });
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param expectedValue 调用方提供的 {@code expectedValue} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Long result =
                observe("compare_delete", () -> template.execute(COMPARE_AND_DELETE, List.of(key), expectedValue));
        return result != null && result > 0;
    }

    /**
     * 删除或失效业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param prefix 调用方提供的 {@code prefix} 值
     * @return 按当前规则计算或读取的数值
     */
    @Override
    public long deleteByPrefix(String prefix) {
        long startedNanos = metrics.start();
        long deleted = 0;
        try (RedisConnection connection = connectionFactory.getConnection();
                Cursor<byte[]> cursor = connection
                        .keyCommands()
                        .scan(ScanOptions.scanOptions()
                                .match(prefix + "*")
                                .count(200)
                                .build())) {
            List<byte[]> batch = new ArrayList<>(200);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == 200) {
                    deleted += connection.keyCommands().del(batch.toArray(byte[][]::new));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                deleted += connection.keyCommands().del(batch.toArray(byte[][]::new));
            }
            metrics.record(
                    "cc4c.cache.redis.operations", startedNanos, "operation", "scan_delete", "outcome", "success");
            return deleted;
        } catch (RuntimeException exception) {
            metrics.record("cc4c.cache.redis.operations", startedNanos, "operation", "scan_delete", "outcome", "error");
            throw exception;
        }
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean ping() {
        return observe("ping", () -> {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        });
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     */
    @Override
    public void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param uri 调用方提供的 {@code uri} 值
     * @param standalone 调用方提供的 {@code standalone} 值
     */
    private void configureCredentials(URI uri, RedisStandaloneConfiguration standalone) {
        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isEmpty()) {
            return;
        }
        int separator = userInfo.indexOf(':');
        if (separator < 0) {
            standalone.setPassword(RedisPassword.of(userInfo));
            return;
        }
        String username = userInfo.substring(0, separator);
        String password = userInfo.substring(separator + 1);
        if (!username.isBlank()) {
            standalone.setUsername(username);
        }
        standalone.setPassword(RedisPassword.of(password));
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param uri 调用方提供的 {@code uri} 值
     * @param standalone 调用方提供的 {@code standalone} 值
     */
    private void configureDatabase(URI uri, RedisStandaloneConfiguration standalone) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return;
        }
        try {
            standalone.setDatabase(Integer.parseInt(path.substring(1)));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CC4C cache Redis URL contains an invalid database number", exception);
        }
    }

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param <T> 方法使用的类型参数
     * @param operation 调用方提供的 {@code operation} 值
     * @param action 调用方提供的 {@code action} 值
     * @return 当前操作产生的 T 结果
     */
    private <T> T observe(String operation, java.util.function.Supplier<T> action) {
        long startedNanos = metrics.start();
        try {
            T result = action.get();
            metrics.record("cc4c.cache.redis.operations", startedNanos, "operation", operation, "outcome", "success");
            return result;
        } catch (RuntimeException exception) {
            metrics.record("cc4c.cache.redis.operations", startedNanos, "operation", operation, "outcome", "error");
            throw exception;
        }
    }
}
