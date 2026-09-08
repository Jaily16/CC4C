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

/**
 * 在 Servlet 过滤链中执行共享基础设施检查，并保持请求与响应安全边界。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    private final ObservabilityProperties properties;

    /**
     * 创建 RequestCorrelationFilter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 由容器注入的 ObservabilityProperties 协作组件
     */
    RequestCorrelationFilter(ObservabilityProperties properties) {
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
