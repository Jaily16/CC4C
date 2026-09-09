package com.cc4c.security;

import com.cc4c.config.ObservabilityPortalProperties;
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

/** 通过随机 Cookie 和摘要 Redis 键维护观测会话，续期不能越过绝对有效期。 */
@Service
public final class ObservabilitySessionService {
    public static final String REQUEST_ATTRIBUTE = ObservabilitySessionService.class.getName() + ".activeSession";
    static final String COOKIE_NAME = "CC4C_OBSERVABILITY_SESSION";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final SecurityKeyHasher hasher;
    private final ObjectMapper objectMapper;
    private final ObservabilityPortalProperties properties;

    /**
     * 接入 Redis、Token 摘要、显式 JSON 数据映射及观测会话配置。
     *
     * @param redis 安全状态 Redis 操作入口
     * @param hasher Token 和身份标识的 HMAC 摘要服务
     * @param objectMapper 显式会话字段或响应 JSON 映射器
     * @param properties 观测身份及 Cookie 配置
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
     * 删除请求携带的旧会话后生成新令牌，保存受空闲和绝对期限限制的 Redis 记录并写 Cookie。
     *
     * @param username 观测登录用户名
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     * @return 新会话的身份及两类过期时间
     */
    public ActiveSession replace(String username, HttpServletRequest request, HttpServletResponse response) {
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
     * 校验令牌格式并读取会话；用户名变化或绝对过期时删除记录，否则延长 Redis TTL 至剩余允许期限。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @return 有效且续期成功的会话；缺失或失效时为空
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
     * 只判断请求是否携带观测会话 Cookie，不验证格式或会话有效性。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @return 存在专属 Cookie 时为 true
     */
    boolean hasCookie(HttpServletRequest request) {
        return cookieValue(request) != null;
    }

    /**
     * 仅对格式合法的观测令牌删除对应 Redis 键，不处理业务 Session。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     */
    public void invalidate(HttpServletRequest request) {
        String token = cookieValue(request);
        if (token != null && TOKEN.matcher(token).matches()) {
            redis.delete(key(token));
        }
    }

    /**
     * 写入零有效期的观测会话 Cookie；Redis 撤销由调用方单独执行。
     *
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     */
    public void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /**
     * 读取请求中的第一个观测会话 Cookie。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @return 原始令牌；无 Cookie 时为空
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
     * 以观测命名空间、session 段和 Token 的 HMAC 摘要构造 Redis 键。
     *
     * @param token 观测原始会话令牌，不得记录
     * @return 不含原始 Token 的会话键
     */
    private String key(String token) {
        return properties.sessionNamespace() + ":session:" + hasher.hash(token);
    }

    /**
     * 生成 32 字节安全随机数并以无填充 URL 安全 Base64 编码。
     *
     * @return 43 字符观测会话令牌
     */
    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 取空闲时长与距离绝对到期的剩余时长中的较小值。
     *
     * @param now 本次计算的当前时间
     * @param absoluteExpiresAt 会话绝对过期时间
     * @return 本次写入或续期的 Redis TTL
     */
    private Duration sessionTtl(Instant now, Instant absoluteExpiresAt) {
        Duration remaining = Duration.between(now, absoluteExpiresAt);
        return remaining.compareTo(properties.idleTimeout()) < 0 ? remaining : properties.idleTimeout();
    }

    /**
     * 将显式 SessionValue 字段编码为 JSON，编码失败转换为不含原文的异常。
     *
     * @param value 仅含用户名和时间字段的会话记录
     * @return 待保存的观测会话 JSON
     */
    private String encode(SessionValue value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode an observability session");
        }
    }

    /**
     * 按固定 SessionValue 类型解析 JSON，解析失败转换为不含原文的异常。
     *
     * @param encoded 待解析的观测会话 JSON，不得记录
     * @return 观测会话持久化字段
     */
    private SessionValue decode(String encoded) {
        try {
            return objectMapper.readValue(encoded, SessionValue.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to decode an observability session");
        }
    }

    /**
     * 追加 HttpOnly、SameSite=Strict、路径为 /observability 的观测会话 Cookie。
     *
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     * @param value 待写入 Cookie 的令牌，清除时为空字符串
     * @param maxAge Cookie 有效时长，清除时为零
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
     * 表示当前请求已解析并续期的观测身份及过期时间。
     *
     * @param username 观测登录用户名
     * @param idleExpiresAt 本次续期后的空闲过期时间
     * @param absoluteExpiresAt 会话绝对过期时间
     */
    public record ActiveSession(String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * 持久化观测用户名、创建时间和绝对期限；空闲期限由 Redis TTL 维护。
     *
     * @param username 观测登录用户名
     * @param createdAt 会话创建时间
     * @param absoluteExpiresAt 会话绝对过期时间
     */
    private record SessionValue(String username, Instant createdAt, Instant absoluteExpiresAt) {}
}
