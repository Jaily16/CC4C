package com.cc4c.observability;

import com.cc4c.shared.SecurityKeyHasher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 协调独立观测门户用例及其持久化、安全和外部协作边界。
 */
@Service
final class ObservabilitySessionService {
    static final String REQUEST_ATTRIBUTE = ObservabilitySessionService.class.getName() + ".activeSession";
    static final String COOKIE_NAME = "CC4C_OBSERVABILITY_SESSION";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SecurityKeyHasher hasher;
    private final ObjectMapper objectMapper;
    private final ObservabilityPortalProperties properties;

    /**
     * 创建 ObservabilitySessionService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param redis 调用方提供的 {@code redis} 值
     * @param hasher 调用方提供的 {@code hasher} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param properties 由容器注入的 ObservabilityPortalProperties 协作组件
     */
    ObservabilitySessionService(
            StringRedisTemplate redis,
            SecurityKeyHasher hasher,
            ObjectMapper objectMapper,
            ObservabilityPortalProperties properties) {
        this.redis = redis;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前操作产生的 ActiveSession 结果
     */
    ActiveSession replace(String username, HttpServletRequest request, HttpServletResponse response) {
        invalidate(request);
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plus(properties.absoluteTimeout());
        String token = newToken();
        SessionValue value = new SessionValue(username, now, absoluteExpiresAt);
        Duration ttl = sessionTtl(now, absoluteExpiresAt);
        redis.opsForValue().set(key(token), encode(value), ttl);
        writeCookie(response, token, properties.absoluteTimeout());
        return new ActiveSession(username, now.plus(ttl), absoluteExpiresAt);
    }

    /**
     * 规范化观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 当前操作产生的 ActiveSession 结果
     */
    ActiveSession resolve(HttpServletRequest request) {
        String token = cookieValue(request);
        if (token == null || !TOKEN.matcher(token).matches()) {
            return null;
        }
        String redisKey = key(token);
        String encoded = redis.opsForValue().get(redisKey);
        if (encoded == null) {
            return null;
        }
        SessionValue value = decode(encoded);
        Instant now = Instant.now();
        if (!properties.username().equals(value.username())
                || !value.absoluteExpiresAt().isAfter(now)) {
            redis.delete(redisKey);
            return null;
        }
        Duration ttl = sessionTtl(now, value.absoluteExpiresAt());
        if (!Boolean.TRUE.equals(redis.expire(redisKey, ttl))) {
            return null;
        }
        return new ActiveSession(value.username(), now.plus(ttl), value.absoluteExpiresAt());
    }

    /**
     * 判断观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean hasCookie(HttpServletRequest request) {
        return cookieValue(request) != null;
    }

    /**
     * 删除或失效观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     */
    void invalidate(HttpServletRequest request) {
        String token = cookieValue(request);
        if (token != null && TOKEN.matcher(token).matches()) {
            redis.delete(key(token));
        }
    }

    /**
     * 删除或失效观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     */
    void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 按当前协议生成或读取的字符串值
     */
    private String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param token 当前协议使用且不得记录的安全令牌
     * @return 按当前协议生成或读取的字符串值
     */
    private String key(String token) {
        return properties.sessionNamespace() + ":session:" + hasher.hash(token);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param now 调用方提供的 {@code now} 值
     * @param absoluteExpiresAt 当前操作使用的时间点
     * @return 当前操作产生的 Duration 结果
     */
    private Duration sessionTtl(Instant now, Instant absoluteExpiresAt) {
        Duration remaining = Duration.between(now, absoluteExpiresAt);
        return remaining.compareTo(properties.idleTimeout()) < 0 ? remaining : properties.idleTimeout();
    }

    /**
     * 编码或保护观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param value 待处理或存储的值
     * @return 按当前协议生成或读取的字符串值
     */
    private String encode(SessionValue value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode an observability session");
        }
    }

    /**
     * 解析观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param encoded 调用方提供的 {@code encoded} 值
     * @return 当前操作产生的 SessionValue 结果
     */
    private SessionValue decode(String encoded) {
        try {
            return objectMapper.readValue(encoded, SessionValue.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to decode an observability session");
        }
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param value 待处理或存储的值
     * @param maxAge 调用方提供的 {@code maxAge} 值
     */
    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/observability")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * ActiveSession 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param username 待认证或查询的账户名
     * @param idleExpiresAt 当前操作使用的时间点
     * @param absoluteExpiresAt 当前操作使用的时间点
     */
    record ActiveSession(String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * SessionValue 以不可变结构承载独立观测门户数据，并保持现有字段语义。
     *
     * @param username 待认证或查询的账户名
     * @param createdAt 当前操作使用的时间点
     * @param absoluteExpiresAt 当前操作使用的时间点
     */
    private record SessionValue(String username, Instant createdAt, Instant absoluteExpiresAt) {}
}
