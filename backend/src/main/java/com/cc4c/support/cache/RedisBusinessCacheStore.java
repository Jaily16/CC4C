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

/** 使用独立 Lettuce 连接实现缓存原子操作，并记录每类 Redis 操作耗时及结果。 */
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
     * 委托主构造器并使用禁用指标实现。
     *
     * @param redisUrl 缓存 Redis 地址，可含凭据，不得记录
     */
    RedisBusinessCacheStore(String redisUrl) {
        this(redisUrl, Cc4cMetrics.disabled());
    }

    /**
     * 保存 Redis 地址及指标，实际连接初始化留给容器生命周期回调。
     *
     * @param redisUrl 缓存 Redis 地址，可含凭据，不得记录
     * @param metrics 缓存 Redis 操作指标记录器
     */
    public RedisBusinessCacheStore(String redisUrl, Cc4cMetrics metrics) {
        this.redisUrl = redisUrl;
        this.metrics = metrics;
    }

    /** 解析 redis/rediss 地址、凭据和库号，创建两秒连接及命令超时的独立连接工厂。 */
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
     * 读取 Redis 字符串键并记录操作结果。
     *
     * @param key 完整缓存数据键
     * @return 键值，不存在时为空
     */
    @Override
    public String get(String key) {
        return observe("get", () -> template.opsForValue().get(key));
    }

    /**
     * 向 Redis 写入字符串及指定 TTL，并记录操作结果。
     *
     * @param key 完整缓存数据键
     * @param value 待封装或存储的缓存值
     * @param ttl 本次写入的基础有效期
     */
    @Override
    public void set(String key, String value, Duration ttl) {
        observe("set", () -> {
            template.opsForValue().set(key, value, ttl);
            return null;
        });
    }

    /**
     * 以 Redis 原子条件写入值及 TTL，只有键不存在时成功。
     *
     * @param key 完整缓存数据键
     * @param value 待封装或存储的缓存值
     * @param ttl 本次写入的基础有效期
     * @return Redis 明确报告写入成功时为 true
     */
    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return observe(
                "set_if_absent",
                () -> Boolean.TRUE.equals(template.opsForValue().setIfAbsent(key, value, ttl)));
    }

    /**
     * 原子增加 Redis 整型值，未返回计数时抛出异常。
     *
     * @param key 完整缓存数据键
     * @return 增加后的计数
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
     * 删除指定 Redis 键并记录操作结果。
     *
     * @param key 完整缓存数据键
     */
    @Override
    public void delete(String key) {
        observe("delete", () -> {
            template.delete(key);
            return null;
        });
    }

    /**
     * 通过 Lua 比较预期令牌并原子删除匹配键。
     *
     * @param key 完整缓存数据键
     * @param expectedValue 仅允许删除匹配此令牌的键
     * @return 脚本实际删除键时为 true
     */
    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Long result =
                observe("compare_delete", () -> template.execute(COMPARE_AND_DELETE, List.of(key), expectedValue));
        return result != null && result > 0;
    }

    /**
     * 以 SCAN 匹配前缀并每 200 个键批量删除；退出时关闭游标和连接，异常记录后传播。
     *
     * @param prefix 调用方批准的隔离键前缀
     * @return 实际删除键数量
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
     * 获取独立缓存连接执行 PING，退出时关闭本次连接。
     *
     * @return 响应为 PONG 时为 true
     */
    @Override
    public boolean ping() {
        return observe("ping", () -> {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        });
    }

    /** 销毁已创建的缓存连接工厂；尚未初始化时无需处理。 */
    @Override
    public void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 从 URL 用户信息解析仅密码或用户名:密码形式，并设置独立 Redis 配置。
     *
     * @param uri 已解析的 Redis 连接 URI
     * @param standalone 待填充的独立 Redis 连接配置
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
     * 将 URL 路径解析为 Redis 库号；无路径或根路径保持默认，非数字路径拒绝初始化。
     *
     * @param uri 已解析的 Redis 连接 URI
     * @param standalone 待填充的独立 Redis 连接配置
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
     * 计时执行 Redis 操作，记录成功或运行异常结果；异常不在存储层旁路。
     *
     * @param <T> 缓存值的数据类型
     * @param operation 低基数缓存操作分类
     * @param action 执行单次 Redis 操作的回调
     * @return 底层操作返回值
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
