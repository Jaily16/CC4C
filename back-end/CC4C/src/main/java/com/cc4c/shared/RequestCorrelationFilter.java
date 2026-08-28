package com.cc4c.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private final ObservabilityProperties properties;

    RequestCorrelationFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = CorrelationIds.normalizeOrGenerate(request.getHeader(CorrelationIds.HEADER));
        request.setAttribute(CorrelationIds.REQUEST_ATTRIBUTE, requestId);
        response.setHeader(CorrelationIds.HEADER, requestId);
        long started = System.nanoTime();
        try (CorrelationIds.Scope ignored = CorrelationIds.open(requestId)) {
            filterChain.doFilter(request, response);
        } finally {
            if (properties.enabled()) {
                Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = bestPattern instanceof String pattern ? pattern : "UNKNOWN";
                int status = response.getStatus();
                String outcome = status >= 500 ? "server_error"
                        : status >= 400 ? "client_error" : "success";
                log.atInfo()
                        .addKeyValue("event", "http_request_completed")
                        .addKeyValue("request_id", requestId)
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route)
                        .addKeyValue("status", status)
                        .addKeyValue("outcome", outcome)
                        .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - started))
                        .log("HTTP request completed");
            }
        }
    }
}
