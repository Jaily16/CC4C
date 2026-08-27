package com.cc4c.shared;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class RedisBusinessCacheStore implements BusinessCacheStore, InitializingBean, DisposableBean {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final String redisUrl;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;

    RedisBusinessCacheStore(String redisUrl) {
        this.redisUrl = redisUrl;
    }

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

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder()
                        .commandTimeout(IO_TIMEOUT)
                        .shutdownTimeout(Duration.ofMillis(100))
                        .clientOptions(ClientOptions.builder()
                                .socketOptions(SocketOptions.builder().connectTimeout(IO_TIMEOUT).build())
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

    @Override
    public String get(String key) {
        return template.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        template.opsForValue().set(key, value, ttl);
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(template.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public long increment(String key) {
        Long result = template.opsForValue().increment(key);
        if (result == null) {
            throw new IllegalStateException("Redis increment returned no result");
        }
        return result;
    }

    @Override
    public void delete(String key) {
        template.delete(key);
    }

    @Override
    public boolean compareAndDelete(String key, String expectedValue) {
        Long result = template.execute(COMPARE_AND_DELETE, List.of(key), expectedValue);
        return result != null && result > 0;
    }

    @Override
    public long deleteByPrefix(String prefix) {
        long deleted = 0;
        try (RedisConnection connection = connectionFactory.getConnection();
                Cursor<byte[]> cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions().match(prefix + "*").count(200).build())) {
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
        }
        return deleted;
    }

    @Override
    public void destroy() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

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
}
