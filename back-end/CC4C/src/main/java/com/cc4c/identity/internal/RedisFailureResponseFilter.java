package com.cc4c.identity.internal;

import com.cc4c.shared.BusinessCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class RedisFailureResponseFilter extends OncePerRequestFilter {
    private final SecurityErrorWriter errorWriter;

    RedisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            errorWriter.write(
                    response,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    BusinessCode.SERVICE_UNAVAILABLE,
                    "安全服务暂时不可用");
        }
    }
}
