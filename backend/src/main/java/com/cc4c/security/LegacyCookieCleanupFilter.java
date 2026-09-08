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

/**
 * 在 Servlet 过滤链中执行身份认证检查，并保持请求与响应安全边界。
 */
@Component
public final class LegacyCookieCleanupFilter extends OncePerRequestFilter {
    private static final Set<String> LEGACY_NAMES = Set.of("user_email", "admin");
    private final SecurityProperties properties;

    /**
     * 创建 LegacyCookieCleanupFilter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 由容器注入的 SecurityProperties 协作组件
     */
    LegacyCookieCleanupFilter(SecurityProperties properties) {
        this.properties = properties;
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
