package com.cc4c.observability;

import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** ObservabilitySessionAuthenticationFilter 仅把有效观测 Session 映射为 OBSERVABILITY 权限。 */
final class ObservabilitySessionAuthenticationFilter extends OncePerRequestFilter {
    private final ObservabilitySessionService sessions;
    private final ObjectMapper objectMapper;

    ObservabilitySessionAuthenticationFilter(ObservabilitySessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ObservabilitySessionService.ActiveSession session;
        try {
            session = sessions.resolve(request);
        } catch (RuntimeException exception) {
            response.setStatus(503);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    new ApiResponse<>(BusinessCode.SERVICE_UNAVAILABLE.code(), false, "观测身份服务暂时不可用"));
            return;
        }
        if (session != null) {
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    session.username(), null, List.of(new SimpleGrantedAuthority("ROLE_OBSERVABILITY")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(ObservabilitySessionService.REQUEST_ATTRIBUTE, session);
        } else if (sessions.hasCookie(request)) {
            sessions.clearCookie(response);
        }
        filterChain.doFilter(request, response);
    }
}
