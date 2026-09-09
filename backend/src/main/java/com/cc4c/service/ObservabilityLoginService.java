package com.cc4c.service;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.config.ObservabilityPortalProperties;
import com.cc4c.security.ObservabilityRateLimiter;
import com.cc4c.security.ObservabilitySessionService;
import com.cc4c.security.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 使用门户配置中的独立账号认证观测身份，协调限流、审计和自定义 Redis 会话。 */
@Service
public final class ObservabilityLoginService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final ObservabilityPortalProperties properties;
    private final ObservabilityRateLimiter rateLimiter;
    private final ObservabilitySessionService sessions;
    private final SecurityAuditLogger auditLogger;

    /**
     * 接入观测账号配置、登录限流、独立会话及安全审计。
     *
     * @param properties 观测门户独立账号配置
     * @param rateLimiter 观测登录专用频率限制器
     * @param sessions 观测独立会话服务
     * @param auditLogger 安全事件审计服务
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
     * 先检查限流再比对用户名及 BCrypt 密码；拒绝超过 72 个 UTF-8 字节的密码，成功后替换观测会话。
     *
     * @param username 本次观测登录用户名
     * @param password 观测登录明文密码，不得记录
     * @param request 当前 HTTP 请求，提供关联 ID 或会话和来源上下文
     * @param response 接收观测会话 Cookie 的 HTTP 响应
     * @return 新建的观测活动会话
     */
    public ObservabilitySessionService.ActiveSession login(
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
     * 撤销当前观测会话并清理专属 Cookie，记录观测退出审计。
     *
     * @param request 当前 HTTP 请求，提供关联 ID 或会话和来源上下文
     * @param response 接收观测会话 Cookie 的 HTTP 响应
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.invalidate(request);
        sessions.clearCookie(response);
        auditLogger.action(
                "observability_logout", "OBSERVABILITY", properties.username(), "success", request.getRemoteAddr());
    }
}
