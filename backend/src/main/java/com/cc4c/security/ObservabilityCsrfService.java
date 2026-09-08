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

/**
 * 协调独立观测门户用例及其持久化、安全和外部协作边界。
 */
@Component
public final class ObservabilityCsrfService {
    static final String COOKIE_NAME = "CC4C_OBSERVABILITY_XSRF_TOKEN";
    public static final String HEADER_NAME = "X-CC4C-OBSERVABILITY-CSRF";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObservabilityPortalProperties properties;

    /**
     * 创建 ObservabilityCsrfService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 由容器注入的 ObservabilityPortalProperties 协作组件
     */
    ObservabilityCsrfService(ObservabilityPortalProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 按当前协议生成或读取的字符串值
     */
    public String issue(HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        writeCookie(response, token, properties.absoluteTimeout());
        return token;
    }

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
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
     * 删除或失效观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     */
    public void clear(HttpServletResponse response) {
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
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 当前操作产生的 BusinessException 结果
     */
    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "观测请求安全校验失败");
    }
}
