package com.cc4c.identity.internal;

import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.RateLimitException;
import com.cc4c.shared.RedisRateLimiter;
import com.cc4c.shared.SecurityAuditLogger;
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

@Service
/** AuthenticationService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class AuthenticationService {
    private static final int USER_SESSION_SECONDS = 2 * 60 * 60;
    private static final int ADMIN_SESSION_SECONDS = 60 * 60;

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final RedisRateLimiter rateLimiter;
    private final SecurityAuditLogger auditLogger;

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

    public boolean loginUser(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.USER, email, password, request, response);
    }

    public boolean loginAdministrator(
            String adminId, String password, HttpServletRequest request, HttpServletResponse response) {
        return authenticate(AccountRole.ADMIN, adminId, password, request, response);
    }

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
