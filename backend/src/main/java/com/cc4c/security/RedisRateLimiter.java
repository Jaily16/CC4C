package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.common.RateLimitException;
import com.cc4c.config.SecurityProperties;
import com.cc4c.support.monitoring.Cc4cMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * RedisRateLimiter 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
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

    /**
     * 创建 RedisRateLimiter 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redis 调用方提供的 {@code redis} 值
     * @param hasher 调用方提供的 {@code hasher} 值
     * @param properties 调用方提供的 {@code properties} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    @Autowired
    public RedisRateLimiter(
            StringRedisTemplate redis, SecurityKeyHasher hasher, SecurityProperties properties, Cc4cMetrics metrics) {
        this.redis = redis;
        this.hasher = hasher;
        this.prefix = properties.keyPrefix();
        this.metrics = metrics;
    }

    /**
     * 创建 RedisRateLimiter 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redis 调用方提供的 {@code redis} 值
     * @param hasher 调用方提供的 {@code hasher} 值
     * @param properties 调用方提供的 {@code properties} 值
     */
    public RedisRateLimiter(StringRedisTemplate redis, SecurityKeyHasher hasher, SecurityProperties properties) {
        this(redis, hasher, properties, Cc4cMetrics.disabled());
    }

    /**
     * 校验 RedisRateLimiter 中与 checkLogin 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param accountType 调用方提供的 {@code accountType} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
     */
    public void checkLogin(String accountType, String identifier, String remoteAddress) {
        checkWithin("login:ip:" + hasher.hash(remoteAddress), 20, "login_ip");
        checkWithin(loginAccountKey(accountType, identifier), 5, "login_account");
    }

    /**
     * 执行 RedisRateLimiter 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param accountType 调用方提供的 {@code accountType} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
     */
    public void loginFailed(String accountType, String identifier, String remoteAddress) {
        requireWithin("login:ip:" + hasher.hash(remoteAddress), 20, Duration.ofMinutes(15), "login_ip");
        requireWithin(loginAccountKey(accountType, identifier), 5, Duration.ofMinutes(15), "login_account");
    }

    /**
     * 执行 RedisRateLimiter 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param accountType 调用方提供的 {@code accountType} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     */
    public void loginSucceeded(String accountType, String identifier) {
        redis.delete(key(loginAccountKey(accountType, identifier)));
    }

    /**
     * 校验 RedisRateLimiter 中与 checkVerificationEmail 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param email 调用方提供的 {@code email} 值
     */
    public void checkVerificationEmail(String email) {
        String subject = hasher.hash(email.trim().toLowerCase(Locale.ROOT));
        requireWithin("email:cooldown:" + subject, 1, Duration.ofSeconds(60), "verification_email_cooldown");
        requireWithin("email:hour:" + subject, 5, Duration.ofHours(1), "verification_email_hour");
    }

    /**
     * 校验 RedisRateLimiter 中与 checkComment 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param userId 目标对象的稳定标识
     */
    public void checkComment(long userId) {
        requireWithin("comment:user:" + hasher.hash(Long.toString(userId)), 10, Duration.ofMinutes(1), "comment_user");
    }

    /**
     * 校验 RedisRateLimiter 中与 checkBlogPublish 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param userId 目标对象的稳定标识
     */
    public void checkBlogPublish(long userId) {
        requireWithin("blog:user:" + hasher.hash(Long.toString(userId)), 5, Duration.ofHours(1), "blog_user");
    }

    /**
     * 执行 RedisRateLimiter 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param accountType 调用方提供的 {@code accountType} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String loginAccountKey(String accountType, String identifier) {
        return "login:account:" + accountType.toLowerCase(Locale.ROOT) + ":"
                + hasher.hash(identifier.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 校验 RedisRateLimiter 中与 requireWithin 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param suffix 调用方提供的 {@code suffix} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @param window 调用方提供的 {@code window} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
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

    /**
     * 校验 RedisRateLimiter 中与 checkWithin 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param suffix 调用方提供的 {@code suffix} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
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

    /**
     * 执行 RedisRateLimiter 中的 key 职责，并保持既有权限、事务与副作用边界。
     *
     * @param suffix 调用方提供的 {@code suffix} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String key(String suffix) {
        return prefix + ":rate:" + suffix;
    }

    /**
     * 执行 RedisRateLimiter 中的 rejected 职责，并保持既有权限、事务与副作用边界。
     *
     * @param scope 调用方提供的 {@code scope} 值
     */
    private void rejected(String scope) {
        metrics.increment("cc4c.security.rate.limit.rejections", "scope", scope);
    }
}
