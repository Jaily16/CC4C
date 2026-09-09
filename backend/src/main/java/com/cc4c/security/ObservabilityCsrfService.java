package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.config.ObservabilityPortalProperties;
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

/** 为观测登录和退出签发双提交 CSRF 令牌，并校验精确 Origin 与请求头/Cookie 一致性。 */
@Component
public final class ObservabilityCsrfService {
    static final String COOKIE_NAME = "CC4C_OBSERVABILITY_XSRF_TOKEN";
    public static final String HEADER_NAME = "X-CC4C-OBSERVABILITY-CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObservabilityPortalProperties properties;

    /**
     * 保存观测允许来源、Cookie 安全标志和令牌有效期配置。
     *
     * @param properties 观测身份及 Cookie 配置
     */
    ObservabilityCsrfService(ObservabilityPortalProperties properties) {
        this.properties = properties;
    }

    /**
     * 生成 32 字节安全随机令牌，以 URL 安全 Base64 写入观测 CSRF Cookie 并返回正文使用值。
     *
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     * @return 无填充的 CSRF 令牌
     */
    public String issue(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        writeCookie(response, token, properties.absoluteTimeout());
        return token;
    }

    /**
     * 要求 Origin 精确匹配，且专属请求头与 Cookie 都存在并通过常量时间字节比对。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     */
    public void validate(HttpServletRequest request) {
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

    /**
     * 写入零有效期的观测 CSRF Cookie，使浏览器移除它。
     *
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     */
    public void clear(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    /**
     * 从请求中读取第一个名称匹配的观测 CSRF Cookie。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @return Cookie 值；不存在时为空
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
     * 追加 HttpOnly、SameSite=Strict、路径为 /observability 的专属 CSRF Cookie。
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
     * 构造不暴露具体失败项的 403 观测安全校验异常。
     *
     * @return 观测请求被拒绝的业务异常
     */
    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "观测请求安全校验失败");
    }
}
