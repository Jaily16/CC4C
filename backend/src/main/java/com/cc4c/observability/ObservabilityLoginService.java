package com.cc4c.observability;

import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 协调独立观测门户用例及其持久化、安全和外部协作边界。
 */
@Service
final class ObservabilityLoginService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final ObservabilityPortalProperties properties;
    private final ObservabilityRateLimiter rateLimiter;
    private final ObservabilitySessionService sessions;
    private final SecurityAuditLogger auditLogger;

    /**
     * 创建 ObservabilityLoginService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 由容器注入的 ObservabilityPortalProperties 协作组件
     * @param rateLimiter 调用方提供的 {@code rateLimiter} 值
     * @param sessions 由容器注入的 ObservabilitySessionService 协作组件
     * @param auditLogger 调用方提供的 {@code auditLogger} 值
     */
    ObservabilityLoginService(
            ObservabilityPortalProperties properties,
            ObservabilityRateLimiter rateLimiter,
            ObservabilitySessionService sessions,
            SecurityAuditLogger auditLogger) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.sessions = sessions;
        this.auditLogger = auditLogger;
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param username 待认证或查询的账户名
     * @param password 仅用于当前安全校验的密码或密码摘要
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前操作产生的 ObservabilitySessionService.ActiveSession 结果
     */
    ObservabilitySessionService.ActiveSession login(
            String username, String password, HttpServletRequest request, HttpServletResponse response) {
        String remoteAddress = request.getRemoteAddr();
        rateLimiter.check(username, remoteAddress);
        boolean usernameMatches = MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8), properties.username().getBytes(StandardCharsets.UTF_8));
        boolean passwordShape = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean passwordMatches = passwordShape && encoder.matches(password, properties.passwordHash());
        if (!usernameMatches || !passwordMatches) {
            auditLogger.authentication("OBSERVABILITY", username, "failure", remoteAddress);
            rateLimiter.failed(username, remoteAddress);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "账号或密码错误");
        }
        rateLimiter.succeeded(username);
        auditLogger.authentication("OBSERVABILITY", username, "success", remoteAddress);
        return sessions.replace(properties.username(), request, response);
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     */
    void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.invalidate(request);
        sessions.clearCookie(response);
        auditLogger.action(
                "observability_logout", "OBSERVABILITY", properties.username(), "success", request.getRemoteAddr());
    }
}
