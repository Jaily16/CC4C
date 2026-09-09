package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 捕获后续过滤链中的 Redis 基础设施运行异常，在响应尚未提交时转换为 503。 */
public final class RedisFailureResponseFilter extends OncePerRequestFilter {
    private final SecurityErrorWriter errorWriter;

    /**
     * 接入统一安全错误响应写入器。
     *
     * @param errorWriter 统一安全错误响应写入器
     */
    public RedisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 先执行后续过滤链；仅对可识别 Redis 故障且未提交的响应写入 503，其他异常原样传播。
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
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            if (!RedisInfrastructureFailure.isUnavailable(exception)) {
                throw exception;
            }
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
