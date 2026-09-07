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

/** ObservabilitySessionService 以哈希键保存独立 Redis Session，绝不持久化原始浏览器 Token。 */
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

    boolean hasCookie(HttpServletRequest request) {
        return cookieValue(request) != null;
    }

    void invalidate(HttpServletRequest request) {
        String token = cookieValue(request);
        if (token != null && TOKEN.matcher(token).matches()) {
            redis.delete(key(token));
        }
    }

    void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

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

    private String key(String token) {
        return properties.sessionNamespace() + ":session:" + hasher.hash(token);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Duration sessionTtl(Instant now, Instant absoluteExpiresAt) {
        Duration remaining = Duration.between(now, absoluteExpiresAt);
        return remaining.compareTo(properties.idleTimeout()) < 0 ? remaining : properties.idleTimeout();
    }

    private String encode(SessionValue value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode an observability session");
        }
    }

    private SessionValue decode(String encoded) {
        try {
            return objectMapper.readValue(encoded, SessionValue.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to decode an observability session");
        }
    }

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

    record ActiveSession(String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    private record SessionValue(String username, Instant createdAt, Instant absoluteExpiresAt) {}
}
