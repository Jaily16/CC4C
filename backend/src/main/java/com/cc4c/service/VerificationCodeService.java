package com.cc4c.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.config.SecurityProperties;
import com.cc4c.dto.IdentityDtos.VerificationPurpose;
import com.cc4c.entity.UserEntity;
import com.cc4c.mapper.UserMapper;
import com.cc4c.security.RedisRateLimiter;
import com.cc4c.security.SecurityKeyHasher;
import com.cc4c.security.VerificationCodeGenerator;
import com.cc4c.support.messaging.AsyncEventTypes;
import com.cc4c.support.messaging.TransactionalOutbox;
import com.cc4c.support.messaging.VerificationEmailRequestedV1;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以 Outbox 申请验证码邮件，在投递时激活 Redis 摘要，并按邮箱及用途原子消费验证码。 */
@Service
public class VerificationCodeService {
    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            """
            local expected = redis.call('HGET', KEYS[1], 'digest')
            if not expected then return 0 end
            if expected == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= 5 then redis.call('DEL', KEYS[1]) end
            return -1
            """,
            Long.class);
    private static final DefaultRedisScript<Long> ACTIVATE_SCRIPT = new DefaultRedisScript<>(
            """
            local currentIssuedAt = redis.call('HGET', KEYS[1], 'issuedAt')
            if currentIssuedAt and tonumber(currentIssuedAt) > tonumber(ARGV[1]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'eventId', ARGV[2],
                'issuedAt', ARGV[1],
                'digest', ARGV[3],
                'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """,
            Long.class);
    private static final DefaultRedisScript<Long> DISCARD_SCRIPT = new DefaultRedisScript<>(
            """
            local currentEventId = redis.call('HGET', KEYS[1], 'eventId')
            if currentEventId and currentEventId == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final VerificationCodeGenerator generator;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final SecurityKeyHasher hasher;
    private final RedisRateLimiter rateLimiter;
    private final TransactionalOutbox outbox;
    private final String keyPrefix;

    /**
     * 接入验证码生成、账户查询、Redis 脚本、带秘密的摘要计算、限流及事务 Outbox。
     *
     * @param generator 六位数字验证码生成器
     * @param userMapper 用户数据访问 Mapper
     * @param redis 验证码摘要及状态的 Redis 操作入口
     * @param hasher 带秘密 pepper 的安全摘要服务
     * @param rateLimiter 基于 Redis 的业务频率限制器
     * @param outbox 在业务事务中追加加密事件的 Outbox 服务
     * @param properties 提供验证码安全键前缀的配置
     */
    VerificationCodeService(
            VerificationCodeGenerator generator,
            UserMapper userMapper,
            StringRedisTemplate redis,
            SecurityKeyHasher hasher,
            RedisRateLimiter rateLimiter,
            TransactionalOutbox outbox,
            SecurityProperties properties) {
        this.generator = generator;
        this.userMapper = userMapper;
        this.redis = redis;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.outbox = outbox;
        this.keyPrefix = properties.keyPrefix();
    }

    /**
     * 检查邮箱发送频率后按注册或重置用途决定是否追加十分钟有效的邮件事件；不适用的邮箱也返回成功以避免枚举账户。
     *
     * @param recipient 验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @return 申请处理完成时为 true，不表示邮件已送达
     */
    @Transactional
    public boolean send(String recipient, VerificationPurpose purpose) {
        String normalizedEmail = normalize(recipient);
        rateLimiter.checkVerificationEmail(normalizedEmail);
        boolean exists =
                userMapper.exists(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, normalizedEmail));
        boolean shouldSend = purpose == VerificationPurpose.REGISTER ? !exists : exists;
        if (!shouldSend) {
            return true;
        }

        Instant occurredAt = Instant.now();
        outbox.append(
                AsyncEventTypes.VERIFICATION_EMAIL_REQUESTED,
                "verification",
                hasher.hash(normalizedEmail + ":" + purpose.name()),
                new VerificationEmailRequestedV1(normalizedEmail, purpose, generator.generate()),
                occurredAt,
                occurredAt.plus(VALIDITY));
        return true;
    }

    /**
     * 通过 Redis 脚本比对摘要，成功即删除；错误次数达到五次也删除，失效或不匹配时抛出验证码错误。
     *
     * @param email 账户或验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @param code 待签发或比对的明文验证码，不得记录
     */
    public void consume(String email, VerificationPurpose purpose, String code) {
        String normalizedEmail = normalize(email);
        Long result = redis.execute(
                CONSUME_SCRIPT, List.of(key(normalizedEmail, purpose)), digest(normalizedEmail, purpose, code));
        if (result == null || result != 1) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.INVALID_VERIFICATION_CODE, "验证码错误、已过期或已使用");
        }
    }

    /**
     * 按剩余有效期激活摘要并清零尝试数；过期事件或早于已激活签发时间的事件不覆盖当前验证码。
     *
     * @param email 账户或验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @param code 待签发或比对的明文验证码，不得记录
     * @param eventId 验证码邮件事件唯一标识
     * @param issuedAt 验证码事件签发时间
     * @param expiresAt 验证码业务有效期截止时间
     * @return 本次验证码已激活时为 true
     */
    public boolean activateForDelivery(
            String email,
            VerificationPurpose purpose,
            String code,
            String eventId,
            Instant issuedAt,
            Instant expiresAt) {
        long remainingMillis = Duration.between(Instant.now(), expiresAt).toMillis();
        if (remainingMillis <= 0) {
            return false;
        }
        String normalizedEmail = normalize(email);
        Long result = redis.execute(
                ACTIVATE_SCRIPT,
                List.of(key(normalizedEmail, purpose)),
                Long.toString(issuedAt.toEpochMilli()),
                eventId,
                digest(normalizedEmail, purpose, code),
                Long.toString(remainingMillis));
        return result != null && result == 1;
    }

    /**
     * 仅当 Redis 中当前事件 ID 与指定事件相同时删除验证码，避免撤销较新的签发。
     *
     * @param email 账户或验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @param eventId 验证码邮件事件唯一标识
     */
    public void discardIfCurrent(String email, VerificationPurpose purpose, String eventId) {
        redis.execute(DISCARD_SCRIPT, List.of(key(normalize(email), purpose)), eventId);
    }

    /**
     * 组合安全前缀、小写用途和邮箱摘要，生成不含明文邮箱的验证码键。
     *
     * @param email 账户或验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @return 用途隔离的 Redis 验证码键
     */
    private String key(String email, VerificationPurpose purpose) {
        return keyPrefix + ":verification:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + hasher.hash(email);
    }

    /**
     * 对邮箱、用途和验证码的组合计算摘要，使同一码不能跨邮箱或用途使用。
     *
     * @param email 账户或验证码收件邮箱
     * @param purpose 注册或密码重置用途
     * @param code 待签发或比对的明文验证码，不得记录
     * @return 验证码比对摘要
     */
    private String digest(String email, VerificationPurpose purpose, String code) {
        return hasher.hash(email + ":" + purpose.name() + ":" + code);
    }

    /**
     * 去除邮箱首尾空白并使用 Locale.ROOT 转为小写。
     *
     * @param email 账户或验证码收件邮箱
     * @return 规范化邮箱
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
