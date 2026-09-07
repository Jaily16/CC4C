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

/** ObservabilityLoginService 对单一配置账户执行定时一致的 BCrypt 校验及独立审计。 */
@Service
final class ObservabilityLoginService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final ObservabilityPortalProperties properties;
    private final ObservabilityRateLimiter rateLimiter;
    private final ObservabilitySessionService sessions;
    private final SecurityAuditLogger auditLogger;

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

    void logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.invalidate(request);
        sessions.clearCookie(response);
        auditLogger.action(
                "observability_logout", "OBSERVABILITY", properties.username(), "success", request.getRemoteAddr());
    }
}
