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

/** 以带摘要的 Redis 键限制业务登录失败、验证码申请、评论及博客发布频率。 */
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
     * 接入 Redis、摘要和安全键前缀，并保存拒绝指标记录器。
     *
     * @param redis 安全状态 Redis 操作入口
     * @param hasher Token 和身份标识的 HMAC 摘要服务
     * @param properties 提供业务安全键前缀的配置
     * @param metrics 安全拒绝指标记录器
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
     * 委托主构造器并使用禁用指标实现。
     *
     * @param redis 安全状态 Redis 操作入口
     * @param hasher Token 和身份标识的 HMAC 摘要服务
     * @param properties 提供业务安全键前缀的配置
     */
    public RedisRateLimiter(StringRedisTemplate redis, SecurityKeyHasher hasher, SecurityProperties properties) {
        this(redis, hasher, properties, Cc4cMetrics.disabled());
    }

    /**
     * 只读检查登录 IP 二十次和角色账号五次的失败阈值，先检查 IP。
     *
     * @param accountType 业务账号角色 USER 或 ADMIN
     * @param identifier 用户邮箱或管理员编号
     * @param remoteAddress 本次请求的远端地址
     */
    public void checkLogin(String accountType, String identifier, String remoteAddress) {
        checkWithin("login:ip:" + hasher.hash(remoteAddress), 20, "login_ip");
        checkWithin(loginAccountKey(accountType, identifier), 5, "login_account");
    }

    /**
     * 依次增加 IP 与角色账号的十五分钟失败计数；任一步触发限制即中止后续处理。
     *
     * @param accountType 业务账号角色 USER 或 ADMIN
     * @param identifier 用户邮箱或管理员编号
     * @param remoteAddress 本次请求的远端地址
     */
    public void loginFailed(String accountType, String identifier, String remoteAddress) {
        requireWithin("login:ip:" + hasher.hash(remoteAddress), 20, Duration.ofMinutes(15), "login_ip");
        requireWithin(loginAccountKey(accountType, identifier), 5, Duration.ofMinutes(15), "login_account");
    }

    /**
     * 只删除该角色账号的失败计数，不清除共享 IP 失败计数。
     *
     * @param accountType 业务账号角色 USER 或 ADMIN
     * @param identifier 用户邮箱或管理员编号
     */
    public void loginSucceeded(String accountType, String identifier) {
        redis.delete(key(loginAccountKey(accountType, identifier)));
    }

    /**
     * 依次计数并限制同一邮箱每 60 秒一次、每小时五次验证码申请。
     *
     * @param email 验证码收件邮箱
     */
    public void checkVerificationEmail(String email) {
        String subject = hasher.hash(email.trim().toLowerCase(Locale.ROOT));
        requireWithin("email:cooldown:" + subject, 1, Duration.ofSeconds(60), "verification_email_cooldown");
        requireWithin("email:hour:" + subject, 5, Duration.ofHours(1), "verification_email_hour");
    }

    /**
     * 计数并限制同一用户每分钟十次评论申请。
     *
     * @param userId 当前业务用户 ID
     */
    public void checkComment(long userId) {
        requireWithin("comment:user:" + hasher.hash(Long.toString(userId)), 10, Duration.ofMinutes(1), "comment_user");
    }

    /**
     * 计数并限制同一用户每小时五次博客发布申请。
     *
     * @param userId 当前业务用户 ID
     */
    public void checkBlogPublish(long userId) {
        requireWithin("blog:user:" + hasher.hash(Long.toString(userId)), 5, Duration.ofHours(1), "blog_user");
    }

    /**
     * 使用小写角色与规范化登录标识的摘要生成账号限流键后缀。
     *
     * @param accountType 业务账号角色 USER 或 ADMIN
     * @param identifier 用户邮箱或管理员编号
     * @return 角色隔离且不含明文账号的后缀
     */
    private String loginAccountKey(String accountType, String identifier) {
        return "login:account:" + accountType.toLowerCase(Locale.ROOT) + ":"
                + hasher.hash(identifier.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 通过 Lua 增加固定窗口计数；空结果按 503 处理，超限记录指标并抛出带秒级等待时间的 429。
     *
     * @param suffix 限流键后缀
     * @param limit 固定窗口允许的最大计数
     * @param window 首次计数起算的固定窗口时长
     * @param scope 低基数限流指标分类
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
     * 只读检查已有计数是否达到阈值，不增加计数；空结果按 503、限流按 429 处理。
     *
     * @param suffix 限流键后缀
     * @param limit 固定窗口允许的最大计数
     * @param scope 低基数限流指标分类
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
     * 在业务安全前缀与 rate 段后追加具体限流后缀。
     *
     * @param suffix 限流键后缀
     * @return 完整业务限流 Redis 键
     */
    private String key(String suffix) {
        return prefix + ":rate:" + suffix;
    }

    /**
     * 按固定 scope 标签增加频率限制拒绝指标。
     *
     * @param scope 低基数限流指标分类
     */
    private void rejected(String scope) {
        metrics.increment("cc4c.security.rate.limit.rejections", "scope", scope);
    }
}
