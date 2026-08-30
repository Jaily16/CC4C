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

@Service
/** VerificationCodeService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
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

    public void consume(String email, VerificationPurpose purpose, String code) {
        String normalizedEmail = normalize(email);
        Long result = redis.execute(
                CONSUME_SCRIPT, List.of(key(normalizedEmail, purpose)), digest(normalizedEmail, purpose, code));
        if (result == null || result != 1) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.INVALID_VERIFICATION_CODE, "验证码错误、已过期或已使用");
        }
    }

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

    public void discardIfCurrent(String email, VerificationPurpose purpose, String eventId) {
        redis.execute(DISCARD_SCRIPT, List.of(key(normalize(email), purpose)), eventId);
    }

    private String key(String email, VerificationPurpose purpose) {
        return keyPrefix + ":verification:" + purpose.name().toLowerCase(Locale.ROOT) + ":" + hasher.hash(email);
    }

    private String digest(String email, VerificationPurpose purpose, String code) {
        return hasher.hash(email + ":" + purpose.name() + ":" + code);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
