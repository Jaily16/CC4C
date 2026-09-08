package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 Servlet 过滤链中执行身份认证检查，并保持请求与响应安全边界。
 */
public final class RedisFailureResponseFilter extends OncePerRequestFilter {
    private final SecurityErrorWriter errorWriter;

    /**
     * 创建 RedisFailureResponseFilter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param errorWriter 调用方提供的 {@code errorWriter} 值
     */
    public RedisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
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
