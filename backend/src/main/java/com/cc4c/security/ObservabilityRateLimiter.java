package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.common.RateLimitException;
import com.cc4c.config.ObservabilityPortalProperties;
import com.cc4c.support.monitoring.Cc4cMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 在观测独立命名空间内按账号及 IP 限制失败登录，使用十五分钟固定窗口。 */
@Service
public final class ObservabilityRateLimiter {
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
     * 接入 Redis、摘要和指标，并从观测会话命名空间派生限流前缀。
     *
     * @param redis 安全状态 Redis 操作入口
     * @param hasher Token 和身份标识的 HMAC 摘要服务
     * @param metrics 安全拒绝指标记录器
     * @param properties 观测身份及 Cookie 配置
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
     * 依次检查账号五次、IP 二十次的失败阈值；此步骤不增加计数。
     *
     * @param username 观测登录用户名
     * @param remoteAddress 本次请求的远端地址
     */
    public void check(String username, String remoteAddress) {
        checkKey(accountKey(username), 5, "observability_login_account");
        checkKey(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    /**
     * 按账号和 IP 顺序增加十五分钟窗口失败计数；任一步抛出限流异常即中止。
     *
     * @param username 观测登录用户名
     * @param remoteAddress 本次请求的远端地址
     */
    public void failed(String username, String remoteAddress) {
        increment(accountKey(username), 5, "observability_login_account");
        increment(ipKey(remoteAddress), 20, "observability_login_ip");
    }

    /**
     * 登录成功后只删除该账号的失败计数，保留共享 IP 计数。
     *
     * @param username 观测登录用户名
     */
    public void succeeded(String username) {
        redis.delete(prefix + accountKey(username));
    }

    /**
     * 将用户名去空白并小写化后摘要，生成账号限流键后缀。
     *
     * @param username 观测登录用户名
     * @return 不含明文用户名的账号后缀
     */
    private String accountKey(String username) {
        return "account:" + hasher.hash(username.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 对远端地址摘要并生成 IP 限流键后缀。
     *
     * @param remoteAddress 本次请求的远端地址
     * @return 不含明文地址的 IP 后缀
     */
    private String ipKey(String remoteAddress) {
        return "ip:" + hasher.hash(remoteAddress);
    }

    /**
     * 通过 Lua 增加固定窗口计数，首次计数设置过期；超阈值时执行拒绝处理。
     *
     * @param suffix 限流键后缀
     * @param limit 固定窗口允许的最大计数
     * @param scope 低基数限流指标分类
     */
    private void increment(String suffix, long limit, String scope) {
        Long retry = redis.execute(
                INCREMENT, List.of(prefix + suffix), Long.toString(WINDOW.toMillis()), Long.toString(limit));
        enforce(retry, scope);
    }

    /**
     * 通过 Lua 读取现有计数及剩余 TTL，达到阈值时执行拒绝处理。
     *
     * @param suffix 限流键后缀
     * @param limit 固定窗口允许的最大计数
     * @param scope 低基数限流指标分类
     */
    private void checkKey(String suffix, long limit, String scope) {
        Long retry = redis.execute(CHECK, List.of(prefix + suffix), Long.toString(limit));
        enforce(retry, scope);
    }

    /**
     * 空脚本结果转为 503；非负等待时间记录拒绝指标并向上取整为秒后抛出 429。
     *
     * @param retryMilliseconds Lua 返回的等待毫秒数；负数表示未触发限制
     * @param scope 低基数限流指标分类
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
