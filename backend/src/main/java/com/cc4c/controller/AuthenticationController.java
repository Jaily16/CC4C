package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 向业务前端提供 CSRF 信息及当前用户或管理员会话摘要。 */
@RestController
public class AuthenticationController {
    private final CurrentActor currentActor;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    /**
     * 接入当前身份读取及业务 CSRF Cookie 仓库。
     *
     * @param currentActor 当前业务身份读取接口
     * @param csrfTokenRepository 业务 CSRF Cookie 仓库
     */
    AuthenticationController(CurrentActor currentActor, CookieCsrfTokenRepository csrfTokenRepository) {
        this.currentActor = currentActor;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * 加载或生成业务 CSRF Token，触发延迟令牌写入 Cookie 并返回请求头信息。
     *
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 业务 CSRF 请求头、参数名和令牌
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken =
                csrfTokenRepository.loadDeferredToken(request, response).get();
        return ApiResponse.success(
                new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken()));
    }

    /**
     * 读取当前业务身份；匿名时仅返回未认证标志，其余身份字段为空。
     *
     * @return 当前业务会话摘要
     */
    @GetMapping("/auth/session")
    public ApiResponse<AuthSessionResponse> session() {
        return ApiResponse.success(currentActor
                .current()
                .map(actor -> new AuthSessionResponse(true, actor.role().name(), actor.id(), actor.displayName()))
                .orElseGet(() -> new AuthSessionResponse(false, null, null, null)));
    }

    /**
     * 向业务前端返回 CSRF 请求头名称、表单参数名及令牌。
     *
     * @param headerName CSRF 校验使用的请求头名
     * @param parameterName CSRF 校验支持的表单参数名
     * @param token CSRF 令牌，不得记录
     */
    public record CsrfResponse(String headerName, String parameterName, String token) {}

    /**
     * 表示业务用户或管理员的当前认证状态。
     *
     * @param authenticated 当前业务身份是否已认证
     * @param role USER 或 ADMIN，未认证时为空
     * @param actorId 当前身份 ID，未认证时为空
     * @param displayName 当前身份展示名，未认证时为空
     */
    public record AuthSessionResponse(boolean authenticated, String role, String actorId, String displayName) {}
}
