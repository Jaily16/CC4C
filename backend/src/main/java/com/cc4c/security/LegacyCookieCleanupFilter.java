package com.cc4c.security;

import com.cc4c.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 清理旧版本 user_email 和 admin 身份 Cookie，不使用这些 Cookie 认证。 */
@Component
public final class LegacyCookieCleanupFilter extends OncePerRequestFilter {
    private static final Set<String> LEGACY_NAMES = Set.of("user_email", "admin");
    private final SecurityProperties properties;

    /**
     * 接入 Cookie 的 Secure 标志配置。
     *
     * @param properties 业务安全配置
     */
    LegacyCookieCleanupFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * 发现旧身份 Cookie 时写入同名根路径过期 Cookie，随后继续过滤链。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 待继续执行的 Servlet 过滤链
     * @throws ServletException 后续 Servlet 过滤链失败时抛出
     * @throws IOException 后续链或响应输出发生 I/O 错误时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (LEGACY_NAMES.contains(cookie.getName())) {
                    Cookie expired = new Cookie(cookie.getName(), "");
                    expired.setPath("/");
                    expired.setHttpOnly(true);
                    expired.setSecure(properties.cookieSecure());
                    expired.setMaxAge(0);
                    expired.setAttribute("SameSite", "Lax");
                    response.addCookie(expired);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
