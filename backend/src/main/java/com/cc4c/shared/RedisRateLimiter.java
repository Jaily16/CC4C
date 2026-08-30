package com.cc4c.shared;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
/** RedisRateLimiter 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class RedisRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl < 1 then ttl = tonumber(ARGV[1]) end
                return ttl
            end
            return -1
            """,
            Long.class);
    private static final DefaultRedisScript<Long> CHECK_SCRIPT = new DefaultRedisScript<>(
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
    private final String prefix;
    private final Cc4cMetrics metrics;

    @Autowired
    public RedisRateLimiter(
            StringRedisTemplate redis, SecurityKeyHasher hasher, SecurityProperties properties, Cc4cMetrics metrics) {
        this.redis = redis;
        this.hasher = hasher;
        this.prefix = properties.keyPrefix();
        this.metrics = metrics;
    }

    public RedisRateLimiter(StringRedisTemplate redis, SecurityKeyHasher hasher, SecurityProperties properties) {
        this(redis, hasher, properties, Cc4cMetrics.disabled());
    }

    public void checkLogin(String accountType, String identifier, String remoteAddress) {
        checkWithin("login:ip:" + hasher.hash(remoteAddress), 20, "login_ip");
        checkWithin(loginAccountKey(accountType, identifier), 5, "login_account");
    }

    public void loginFailed(String accountType, String identifier, String remoteAddress) {
        requireWithin("login:ip:" + hasher.hash(remoteAddress), 20, Duration.ofMinutes(15), "login_ip");
        requireWithin(loginAccountKey(accountType, identifier), 5, Duration.ofMinutes(15), "login_account");
    }

    public void loginSucceeded(String accountType, String identifier) {
        redis.delete(key(loginAccountKey(accountType, identifier)));
    }

    public void checkVerificationEmail(String email) {
        String subject = hasher.hash(email.trim().toLowerCase(Locale.ROOT));
        requireWithin("email:cooldown:" + subject, 1, Duration.ofSeconds(60), "verification_email_cooldown");
        requireWithin("email:hour:" + subject, 5, Duration.ofHours(1), "verification_email_hour");
    }

    public void checkComment(long userId) {
        requireWithin("comment:user:" + hasher.hash(Long.toString(userId)), 10, Duration.ofMinutes(1), "comment_user");
    }

    public void checkBlogPublish(long userId) {
        requireWithin("blog:user:" + hasher.hash(Long.toString(userId)), 5, Duration.ofHours(1), "blog_user");
    }

    private String loginAccountKey(String accountType, String identifier) {
        return "login:account:" + accountType.toLowerCase(Locale.ROOT) + ":"
                + hasher.hash(identifier.trim().toLowerCase(Locale.ROOT));
    }

    private void requireWithin(String suffix, long limit, Duration window, String scope) {
        Long retryMilliseconds = redis.execute(
                LIMIT_SCRIPT, List.of(key(suffix)), Long.toString(window.toMillis()), Long.toString(limit));
        if (retryMilliseconds == null) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    BusinessCode.SERVICE_UNAVAILABLE,
                    "安全服务暂时不可用");
        }
        if (retryMilliseconds >= 0) {
            rejected(scope);
            throw new RateLimitException((retryMilliseconds + 999) / 1000, scope);
        }
    }

    private void checkWithin(String suffix, long limit, String scope) {
        Long retryMilliseconds = redis.execute(CHECK_SCRIPT, List.of(key(suffix)), Long.toString(limit));
        if (retryMilliseconds == null) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    BusinessCode.SERVICE_UNAVAILABLE,
                    "安全服务暂时不可用");
        }
        if (retryMilliseconds >= 0) {
            rejected(scope);
            throw new RateLimitException((retryMilliseconds + 999) / 1000, scope);
        }
    }

    private String key(String suffix) {
        return prefix + ":rate:" + suffix;
    }

    private void rejected(String scope) {
        metrics.increment("cc4c.security.rate.limit.rejections", "scope", scope);
    }
}
