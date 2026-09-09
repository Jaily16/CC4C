package com.cc4c.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/** 兼容 SPA 明文 CSRF 请求头与 Spring XOR 表单令牌，不处理异步消息。 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    /**
     * 使用 XOR 处理器设置请求属性，并主动求值延迟令牌以生成 Cookie。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param csrfToken 延迟加载或生成 CSRF 令牌的供应器
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    /**
     * 请求头存在时按明文规则解析，否则交给 XOR 处理器解析表单令牌。
     *
     * @param request 当前 HTTP 请求
     * @param csrfToken 当前请求的 CSRF 令牌
     * @return 从当前请求解析的 CSRF 值
     */
    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return request.getHeader(csrfToken.getHeaderName()) != null
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
