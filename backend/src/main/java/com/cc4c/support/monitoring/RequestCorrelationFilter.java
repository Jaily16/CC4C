package com.cc4c.support.monitoring;

import com.cc4c.common.CorrelationIds;
import com.cc4c.config.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** 为 HTTP 请求建立关联 ID，在响应中回传，并按观测开关记录路由模板、状态和耗时。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private final ObservabilityProperties properties;

    /**
     * 保存控制请求完成事件记录的观测开关。
     *
     * @param properties 该组件使用的观测或 Prometheus 设置
     */
    RequestCorrelationFilter(ObservabilityProperties properties) {
        this.properties = properties;
    }

    /**
     * 规范化请求关联 ID，放入属性、响应头及 MDC 作用域；过滤链退出后按路由模板记录结果。
     *
     * @param request 携带可选关联 ID 的 HTTP 请求
     * @param response 写入关联 ID 响应头并读取最终状态的 HTTP 响应
     * @param filterChain 后续 Servlet 过滤链
     * @throws ServletException 后续过滤链处理请求失败时抛出
     * @throws IOException 后续过滤链读写请求或响应失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
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
                String outcome = status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
                log.atInfo()
                        .addKeyValue("event", "http_request_completed")
                        .addKeyValue("request_id", requestId)
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route)
                        .addKeyValue("status", status)
                        .addKeyValue("outcome", outcome)
                        .addKeyValue("duration_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .log("HTTP request completed");
            }
        }
    }
}
