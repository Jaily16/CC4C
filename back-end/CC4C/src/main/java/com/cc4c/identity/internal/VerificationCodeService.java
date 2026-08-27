package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.RedisRateLimiter;
import com.cc4c.shared.SecurityKeyHasher;
import com.cc4c.shared.SecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class VerificationCodeService {
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local expected = redis.call('HGET', KEYS[1], 'digest')
            if not expected then return 0 end
            if expected == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= 5 then redis.call('DEL', KEYS[1]) end
            return -1
            """, Long.class);

    private final EmailSender emailSender;
    private final VerificationCodeGenerator generator;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final SecurityKeyHasher hasher;
    private final RedisRateLimiter rateLimiter;
    private final String from;
    private final String keyPrefix;

    VerificationCodeService(
            EmailSender emailSender,
            VerificationCodeGenerator generator,
            UserMapper userMapper,
            StringRedisTemplate redis,
            SecurityKeyHasher hasher,
            RedisRateLimiter rateLimiter,
            SecurityProperties properties,
            @Value("${spring.mail.username}") String from) {
        this.emailSender = emailSender;
        this.generator = generator;
        this.userMapper = userMapper;
        this.redis = redis;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.from = from;
        this.keyPrefix = properties.keyPrefix();
    }

    public boolean send(String recipient, VerificationPurpose purpose) {
        String normalizedEmail = normalize(recipient);
        rateLimiter.checkVerificationEmail(normalizedEmail);
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, normalizedEmail));
        boolean shouldSend = purpose == VerificationPurpose.REGISTER ? !exists : exists;
        if (!shouldSend) {
            return true;
        }

        String code = generator.generate();
        String key = key(normalizedEmail, purpose);
        redis.opsForHash().put(key, "digest", digest(normalizedEmail, purpose, code));
        redis.opsForHash().put(key, "attempts", "0");
        redis.expire(key, Duration.ofMinutes(10));
        if (!emailSender.send(code, from, recipient)) {
            redis.delete(key);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, BusinessCode.INTERNAL_ERROR,
                    "Fail to Send Email!");
        }
        return true;
    }

    public void consume(String email, VerificationPurpose purpose, String code) {
        String normalizedEmail = normalize(email);
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(key(normalizedEmail, purpose)),
                digest(normalizedEmail, purpose, code));
        if (result == null || result != 1) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.INVALID_VERIFICATION_CODE,
                    "验证码错误、已过期或已使用");
        }
    }

    private String key(String email, VerificationPurpose purpose) {
        return keyPrefix + ":verification:" + purpose.name().toLowerCase(Locale.ROOT)
                + ":" + hasher.hash(email);
    }

    private String digest(String email, VerificationPurpose purpose, String code) {
        return hasher.hash(email + ":" + purpose.name() + ":" + code);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
