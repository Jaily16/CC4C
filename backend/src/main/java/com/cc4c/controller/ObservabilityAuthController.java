package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.ObservabilityDtos.LoginRequest;
import com.cc4c.dto.ObservabilityDtos.LoginResponse;
import com.cc4c.dto.ObservabilityDtos.ObservabilityCsrfResponse;
import com.cc4c.dto.ObservabilityDtos.SessionResponse;
import com.cc4c.security.ObservabilityCsrfService;
import com.cc4c.security.ObservabilitySessionService;
import com.cc4c.service.ObservabilityLoginService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供观测门户独立登录、退出及会话探测，使用专属 CSRF 服务。 */
@RestController
@RequestMapping("/observability/auth")
public class ObservabilityAuthController {
    private final ObservabilityCsrfService csrf;
    private final ObservabilityLoginService loginService;

    /**
     * 接入观测 CSRF 签发校验及独立登录服务。
     *
     * @param csrf 观测 CSRF 签发和校验服务
     * @param loginService 观测独立登录与注销服务
     */
    ObservabilityAuthController(ObservabilityCsrfService csrf, ObservabilityLoginService loginService) {
        this.csrf = csrf;
        this.loginService = loginService;
    }

    /**
     * 签发观测专属 CSRF Token，并通过响应 Cookie 和正文提供给页面。
     *
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 观测 CSRF 请求头名称及令牌
     */
    @GetMapping("/csrf")
    @Operation(operationId = "observabilityCsrf", summary = "签发观测门户 CSRF Token")
    public ApiResponse<ObservabilityCsrfResponse> csrf(HttpServletResponse response) {
        return ApiResponse.success(
                new ObservabilityCsrfResponse(ObservabilityCsrfService.HEADER_NAME, csrf.issue(response)));
    }

    /**
     * 先校验观测 CSRF，再建立独立会话并清除本次 CSRF Cookie。
     *
     * @param body 观测用户名和密码请求
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 观测身份及会话过期时间
     */
    @PostMapping("/login")
    @Operation(operationId = "observabilityLogin", summary = "登录独立观测门户")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        csrf.validate(request);
        ObservabilitySessionService.ActiveSession session =
                loginService.login(body.username(), body.password(), request, response);
        csrf.clear(response);
        return ApiResponse.success(new LoginResponse(
                "OBSERVABILITY", session.username(), session.idleExpiresAt(), session.absoluteExpiresAt()));
    }

    /**
     * 读取过滤器放入请求属性的观测会话；没有活动会话时返回未认证。
     *
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @return 观测会话状态摘要
     */
    @GetMapping("/session")
    @Operation(operationId = "observabilitySession", summary = "读取观测门户 Session 状态")
    public ApiResponse<SessionResponse> session(HttpServletRequest request) {
        Object value = request.getAttribute(ObservabilitySessionService.REQUEST_ATTRIBUTE);
        if (!(value instanceof ObservabilitySessionService.ActiveSession session)) {
            return ApiResponse.success(new SessionResponse(false, null, null, null, null));
        }
        return ApiResponse.success(new SessionResponse(
                true, "OBSERVABILITY", session.username(), session.idleExpiresAt(), session.absoluteExpiresAt()));
    }

    /**
     * 校验观测 CSRF 后注销独立会话，并清除观测 CSRF Cookie。
     *
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 观测退出成功标志
     */
    @PostMapping("/logout")
    @Operation(operationId = "observabilityLogout", summary = "注销观测门户 Session")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        csrf.validate(request);
        loginService.logout(request, response);
        csrf.clear(response);
        return ApiResponse.success(true);
    }
}
