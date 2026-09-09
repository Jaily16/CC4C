package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.dto.ApiResponse;
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

/** 从观测专属 Cookie 恢复独立身份，向请求附加会话摘要，并转换身份存储异常为 503。 */
public final class ObservabilitySessionAuthenticationFilter extends OncePerRequestFilter {
    private final ObservabilitySessionService sessions;
    private final ObjectMapper objectMapper;

    /**
     * 接入观测会话解析和 JSON 错误响应映射器。
     *
     * @param sessions 观测独立会话解析服务
     * @param objectMapper 显式会话字段或响应 JSON 映射器
     */
    public ObservabilitySessionAuthenticationFilter(ObservabilitySessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析成功时设置 OBSERVABILITY 身份；无效 Cookie 被清理，解析运行异常返回 503 并停止后续链。
     *
     * @param request 当前 HTTP 请求，提供专属 Cookie 和请求头
     * @param response 接收 Cookie 或错误正文的 HTTP 响应
     * @param filterChain 待继续执行的 Servlet 过滤链
     * @throws ServletException 后续 Servlet 过滤链失败时抛出
     * @throws IOException 写入错误响应或执行后续链发生 I/O 失败时抛出
     */
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
