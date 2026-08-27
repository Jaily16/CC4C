package com.cc4c.identity.internal;

import com.cc4c.shared.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
final class LegacyCookieCleanupFilter extends OncePerRequestFilter {
    private static final Set<String> LEGACY_NAMES = Set.of("user_email", "admin");
    private final SecurityProperties properties;

    LegacyCookieCleanupFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
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
