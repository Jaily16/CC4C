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

/**
 * ObservabilityRateLimiter 负责独立观测门户的一项明确运行职责，并保持现有外部行为不变。
 */
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

    /**
     * 创建 ObservabilityRateLimiter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redis 调用方提供的 {@code redis} 值
     * @param hasher 调用方提供的 {@code hasher} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     * @param properties 由容器注入的 ObservabilityPortalProperties 协作组件
     */
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

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
     */
    void check(String username, String remoteAddress) {
        checkKey(accountKey(username), 5, "observability_login_account");
        checkKey(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
     */
    void failed(String username, String remoteAddress) {
        increment(accountKey(username), 5, "observability_login_account");
        increment(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     */
    void succeeded(String username) {
        redis.delete(prefix + accountKey(username));
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     * @return 按当前协议生成或读取的字符串值
     */
    private String accountKey(String username) {
        return "account:" + hasher.hash(username.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private String ipKey(String remoteAddress) {
        return "ip:" + hasher.hash(remoteAddress);
    }

    /**
     * 记录观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param suffix 调用方提供的 {@code suffix} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
    private void increment(String suffix, long limit, String scope) {
        Long retry = redis.execute(
                INCREMENT, List.of(prefix + suffix), Long.toString(WINDOW.toMillis()), Long.toString(limit));
        enforce(retry, scope);
    }

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param suffix 调用方提供的 {@code suffix} 值
     * @param limit 调用方提供的 {@code limit} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
    private void checkKey(String suffix, long limit, String scope) {
        Long retry = redis.execute(CHECK, List.of(prefix + suffix), Long.toString(limit));
        enforce(retry, scope);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param retryMilliseconds 调用方提供的 {@code retryMilliseconds} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
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
