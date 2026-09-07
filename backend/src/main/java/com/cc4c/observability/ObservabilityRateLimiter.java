package com.cc4c.observability;

import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.Cc4cMetrics;
import com.cc4c.shared.RateLimitException;
import com.cc4c.shared.SecurityKeyHasher;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** ObservabilityRateLimiter 使用独立 namespace 限制门户账户和来源地址的失败登录。 */
@Service
final class ObservabilityRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if current > tonumber(ARGV[2]) then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl < 1 then ttl = tonumber(ARGV[1]) end
                return ttl
            end
            return -1
            """,
            Long.class);
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>(
            """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current >= tonumber(ARGV[1]) then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl < 1 then ttl = 1000 end
                return ttl
            end
            return -1
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final SecurityKeyHasher hasher;
    private final Cc4cMetrics metrics;
    private final String prefix;

    ObservabilityRateLimiter(
            StringRedisTemplate redis,
            SecurityKeyHasher hasher,
            Cc4cMetrics metrics,
            ObservabilityPortalProperties properties) {
        this.redis = redis;
        this.hasher = hasher;
        this.metrics = metrics;
        this.prefix = properties.sessionNamespace() + ":rate:";
    }

    void check(String username, String remoteAddress) {
        checkKey(accountKey(username), 5, "observability_login_account");
        checkKey(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    void failed(String username, String remoteAddress) {
        increment(accountKey(username), 5, "observability_login_account");
        increment(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    void succeeded(String username) {
        redis.delete(prefix + accountKey(username));
    }

    private String accountKey(String username) {
        return "account:" + hasher.hash(username.trim().toLowerCase(Locale.ROOT));
    }

    private String ipKey(String remoteAddress) {
        return "ip:" + hasher.hash(remoteAddress);
    }

    private void increment(String suffix, long limit, String scope) {
        Long retry = redis.execute(
                INCREMENT, List.of(prefix + suffix), Long.toString(WINDOW.toMillis()), Long.toString(limit));
        enforce(retry, scope);
    }

    private void checkKey(String suffix, long limit, String scope) {
        Long retry = redis.execute(CHECK, List.of(prefix + suffix), Long.toString(limit));
        enforce(retry, scope);
    }

    private void enforce(Long retryMilliseconds, String scope) {
        if (retryMilliseconds == null) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, BusinessCode.SERVICE_UNAVAILABLE, "观测身份服务暂时不可用");
        }
        if (retryMilliseconds >= 0) {
            metrics.increment("cc4c.security.rate.limit.rejections", "scope", scope);
            throw new RateLimitException((retryMilliseconds + 999) / 1000, scope);
        }
    }
}
