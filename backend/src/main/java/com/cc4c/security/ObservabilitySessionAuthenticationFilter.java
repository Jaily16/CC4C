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

/**
 * 在 Servlet 过滤链中执行独立观测门户检查，并保持请求与响应安全边界。
 */
public final class ObservabilitySessionAuthenticationFilter extends OncePerRequestFilter {
    private final ObservabilitySessionService sessions;
    private final ObjectMapper objectMapper;

    /**
     * 创建 ObservabilitySessionAuthenticationFilter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param sessions 由容器注入的 ObservabilitySessionService 协作组件
     * @param objectMapper 应用统一配置的 JSON 映射器
     */
    public ObservabilitySessionAuthenticationFilter(ObservabilitySessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    /**
     * 在当前请求进入后续过滤链前执行安全处理，并保证响应边界保持一致。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param filterChain 调用方提供的 {@code filterChain} 值
     * @throws ServletException 当输入、数据或依赖状态不满足当前方法约束时抛出
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
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
