package com.cc4c.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * 消费并处理身份认证消息，遵循既有幂等与重试边界。
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    /**
     * 处理当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param csrfToken 当前协议使用且不得记录的安全令牌
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    /**
     * 规范化当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param csrfToken 当前协议使用且不得记录的安全令牌
     * @return 按当前协议生成或读取的字符串值
     */
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return request.getHeader(csrfToken.getHeaderName()) != null
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
