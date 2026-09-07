package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.RedisRateLimiter;
import com.cc4c.shared.SecurityKeyHasher;
import com.cc4c.shared.SecurityProperties;
import com.cc4c.shared.TransactionalOutbox;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VerificationCodeService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
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
     * 创建 VerificationCodeService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param generator 调用方提供的 {@code generator} 值
     * @param userMapper 调用方提供的 {@code userMapper} 值
     * @param redis 调用方提供的 {@code redis} 值
     * @param hasher 调用方提供的 {@code hasher} 值
     * @param rateLimiter 调用方提供的 {@code rateLimiter} 值
     * @param outbox 调用方提供的 {@code outbox} 值
     * @param properties 调用方提供的 {@code properties} 值
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
     * 按既有可靠消息或邮件协议发送数据，并保留调用方可观察的失败语义。
     *
     * @param recipient 调用方提供的 {@code recipient} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @return 当前条件是否成立
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
     * 处理 VerificationCodeService 的输入或消息，并沿用既有幂等、确认与失败恢复策略。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @param code 调用方提供的 {@code code} 值
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
     * 执行 VerificationCodeService 中的 activateForDelivery 职责，并保持既有权限、事务与副作用边界。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @param code 调用方提供的 {@code code} 值
     * @param eventId 目标对象的稳定标识
     * @param issuedAt 调用方提供的 {@code issuedAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @return 当前条件是否成立
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
     * 执行 VerificationCodeService 中的 discardIfCurrent 职责，并保持既有权限、事务与副作用边界。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @param eventId 目标对象的稳定标识
     */
    public void discardIfCurrent(String email, VerificationPurpose purpose, String eventId) {
        redis.execute(DISCARD_SCRIPT, List.of(key(normalize(email), purpose)), eventId);
    }

    /**
     * 执行 VerificationCodeService 中的 key 职责，并保持既有权限、事务与副作用边界。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String key(String email, VerificationPurpose purpose) {
        return keyPrefix + ":verification:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + hasher.hash(email);
    }

    /**
     * 执行 VerificationCodeService 中的 digest 职责，并保持既有权限、事务与副作用边界。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     * @param code 调用方提供的 {@code code} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String digest(String email, VerificationPurpose purpose, String code) {
        return hasher.hash(email + ":" + purpose.name() + ":" + code);
    }

    /**
     * 按 VerificationCodeService 的既定规则转换输入，不记录凭据或敏感原文。
     *
     * @param email 调用方提供的 {@code email} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
