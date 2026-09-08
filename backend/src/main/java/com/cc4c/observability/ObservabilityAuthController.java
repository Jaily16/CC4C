package com.cc4c.observability;

import com.cc4c.observability.ObservabilityDtos.LoginRequest;
import com.cc4c.observability.ObservabilityDtos.LoginResponse;
import com.cc4c.observability.ObservabilityDtos.ObservabilityCsrfResponse;
import com.cc4c.observability.ObservabilityDtos.SessionResponse;
import com.cc4c.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供独立观测门户 HTTP 接口，完成输入校验、权限边界和统一响应封装。
 */
@RestController
@RequestMapping("/observability/auth")
public class ObservabilityAuthController {
    private final ObservabilityCsrfService csrf;
    private final ObservabilityLoginService loginService;

    /**
     * 创建 ObservabilityAuthController 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param csrf 由容器注入的 ObservabilityCsrfService 协作组件
     * @param loginService 由容器注入的 ObservabilityLoginService 协作组件
     */
    ObservabilityAuthController(ObservabilityCsrfService csrf, ObservabilityLoginService loginService) {
        this.csrf = csrf;
        this.loginService = loginService;
    }

    /**
     * 处理 {@code csrf} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 统一封装且可安全返回客户端的响应
     */
    @GetMapping("/csrf")
    @Operation(operationId = "observabilityCsrf", summary = "签发观测门户 CSRF Token")
    public ApiResponse<ObservabilityCsrfResponse> csrf(HttpServletResponse response) {
        return ApiResponse.success(
                new ObservabilityCsrfResponse(ObservabilityCsrfService.HEADER_NAME, csrf.issue(response)));
    }

    /**
     * 处理 {@code login} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param body 已经过声明式校验的请求体
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 统一封装且可安全返回客户端的响应
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
     * 处理 {@code session} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 统一封装且可安全返回客户端的响应
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
     * 处理 {@code logout} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 统一封装且可安全返回客户端的响应
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
