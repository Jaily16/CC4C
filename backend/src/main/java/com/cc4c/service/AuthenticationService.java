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

/** 协调业务认证、限流审计和 Session 建立与注销，区分 USER 与 ADMIN 的空闲过期时间。 */
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
     * 接入认证管理器、会话策略及持久化仓库，并接入限流、CSRF 和审计服务。
     *
     * @param authenticationManager 业务认证管理器
     * @param sessionAuthenticationStrategy 角色并发、会话固定攻击防护及注册策略
     * @param securityContextRepository 业务会话安全上下文仓库
     * @param csrfTokenRepository 业务 CSRF 令牌仓库
     * @param rateLimiter 基于 Redis 的业务频率限制器
     * @param auditLogger 不记录明文凭据的安全审计服务
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
     * 以 USER 角色使用邮箱和密码执行登录流程。
     *
     * @param email 账户或验证码收件邮箱
     * @param password 登录明文密码，不得记录
     * @param request 当前 HTTP 请求，提供来源地址和会话
     * @param response 接收会话及 CSRF Cookie 变更的 HTTP 响应
     * @return 会话建立成功时为 true，失败抛出业务异常
     */
    public boolean loginUser(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.USER, email, password, request, response);
    }

    /**
     * 以 ADMIN 角色使用管理员编号和密码执行登录流程。
     *
     * @param adminId 管理员编号
     * @param password 登录明文密码，不得记录
     * @param request 当前 HTTP 请求，提供来源地址和会话
     * @param response 接收会话及 CSRF Cookie 变更的 HTTP 响应
     * @return 会话建立成功时为 true，失败抛出业务异常
     */
    public boolean loginAdministrator(
            String adminId, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.ADMIN, adminId, password, request, response);
    }

    /**
     * 使现有业务 Session 失效并清除安全上下文和 CSRF Token；可识别身份时记录退出审计。
     *
     * @param request 当前 HTTP 请求，提供来源地址和会话
     * @param response 接收会话及 CSRF Cookie 变更的 HTTP 响应
     * @return 注销流程完成后为 true
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
     * 先检查限流再认证，成功后废弃旧会话并保存新上下文；用户空闲期限两小时、管理员一小时。认证失败记录审计及限流计数并返回统一错误。
     *
     * @param role 待认证的 USER 或 ADMIN 角色
     * @param identifier 用户邮箱或管理员编号
     * @param password 登录明文密码，不得记录
     * @param request 当前 HTTP 请求，提供来源地址和会话
     * @param response 接收会话及 CSRF Cookie 变更的 HTTP 响应
     * @return 认证及会话保存成功时为 true
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
