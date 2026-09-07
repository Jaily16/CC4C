package com.cc4c.observability;

import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** ObservabilityCsrfService 签发 HttpOnly 双提交 Token，并为状态变更请求校验精确 Origin。 */
@Component
final class ObservabilityCsrfService {
    static final String COOKIE_NAME = "CC4C_OBSERVABILITY_XSRF_TOKEN";
    static final String HEADER_NAME = "X-CC4C-OBSERVABILITY-CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObservabilityPortalProperties properties;

    ObservabilityCsrfService(ObservabilityPortalProperties properties) {
        this.properties = properties;
    }

    String issue(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        writeCookie(response, token, properties.absoluteTimeout());
        return token;
    }

    void validate(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!properties.allowedOrigin().equals(origin)) {
            throw forbidden();
        }
        String header = request.getHeader(HEADER_NAME);
        String cookie = cookieValue(request);
        if (header == null
                || cookie == null
                || !MessageDigest.isEqual(
                        header.getBytes(StandardCharsets.UTF_8), cookie.getBytes(StandardCharsets.UTF_8))) {
            throw forbidden();
        }
    }

    void clear(HttpServletResponse response) {
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

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "观测请求安全校验失败");
    }
}
