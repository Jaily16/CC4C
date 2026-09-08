package com.cc4c.service;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.common.RateLimitException;
import com.cc4c.security.AccountRole;
import com.cc4c.security.Cc4cAuthenticationToken;
import com.cc4c.security.Cc4cPrincipal;
import com.cc4c.security.RedisRateLimiter;
import com.cc4c.security.SecurityAuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

/**
 * AuthenticationService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
public final class AuthenticationService {
    private static final int USER_SESSION_SECONDS = 2 * 60 * 60;
    private static final int ADMIN_SESSION_SECONDS = 60 * 60;

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final RedisRateLimiter rateLimiter;
    private final SecurityAuditLogger auditLogger;

    /**
     * 创建 AuthenticationService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param authenticationManager 调用方提供的 {@code authenticationManager} 值
     * @param sessionAuthenticationStrategy 调用方提供的 {@code sessionAuthenticationStrategy} 值
     * @param securityContextRepository 由容器注入的 SecurityContextRepository 协作组件
     * @param csrfTokenRepository 由容器注入的 CsrfTokenRepository 协作组件
     * @param rateLimiter 调用方提供的 {@code rateLimiter} 值
     * @param auditLogger 调用方提供的 {@code auditLogger} 值
     */
    AuthenticationService(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            RedisRateLimiter rateLimiter,
            SecurityAuditLogger auditLogger) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
    }

    /**
     * 执行 AuthenticationService 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前条件是否成立
     */
    public boolean loginUser(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.USER, email, password, request, response);
    }

    /**
     * 执行 AuthenticationService 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param adminId 目标对象的稳定标识
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前条件是否成立
     */
    public boolean loginAdministrator(
            String adminId, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.ADMIN, adminId, password, request, response);
    }

    /**
     * 执行 AuthenticationService 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前条件是否成立
     */
    public boolean logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Cc4cPrincipal principal =
                authentication != null && authentication.getPrincipal() instanceof Cc4cPrincipal value ? value : null;
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        csrfTokenRepository.saveToken(null, request, response);
        if (principal != null) {
            auditLogger.action(
                    "logout", principal.role().name(), principal.actorId(), "success", request.getRemoteAddr());
        }
        return true;
    }

    /**
     * 执行 AuthenticationService 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param role 调用方提供的 {@code role} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 当前条件是否成立
     */
    private boolean authenticate(
            AccountRole role,
            String identifier,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        String remoteAddress = request.getRemoteAddr();
        try {
            rateLimiter.checkLogin(role.name(), identifier, remoteAddress);
        } catch (RateLimitException exception) {
            auditLogger.authentication(role.name(), identifier, "rate_limited", remoteAddress);
            throw exception;
        }

        Cc4cAuthenticationToken token = new Cc4cAuthenticationToken(role, identifier, password);
        try {
            Authentication authenticated = authenticationManager.authenticate(token);
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            sessionAuthenticationStrategy.onAuthentication(authenticated, request, response);
            HttpSession newSession = request.getSession(false);
            if (newSession == null) {
                throw new IllegalStateException("Authentication did not create a session");
            }
            newSession.setMaxInactiveInterval(role == AccountRole.ADMIN ? ADMIN_SESSION_SECONDS : USER_SESSION_SECONDS);
            securityContextRepository.saveContext(context, request, response);
            if (authenticated instanceof CredentialsContainer credentialsContainer) {
                credentialsContainer.eraseCredentials();
            }
            rateLimiter.loginSucceeded(role.name(), identifier);
            auditLogger.authentication(role.name(), identifier, "success", remoteAddress);
            return true;
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            auditLogger.authentication(role.name(), identifier, "failure", remoteAddress);
            rateLimiter.loginFailed(role.name(), identifier, remoteAddress);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.LOGIN_FAIL, "账号或密码错误");
        }
    }
}
