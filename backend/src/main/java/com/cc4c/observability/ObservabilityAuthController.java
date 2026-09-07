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

/** ObservabilityAuthController 暴露独立门户的 CSRF、登录、Session 和退出边界。 */
@RestController
@RequestMapping("/observability/auth")
public class ObservabilityAuthController {
    private final ObservabilityCsrfService csrf;
    private final ObservabilityLoginService loginService;

    ObservabilityAuthController(ObservabilityCsrfService csrf, ObservabilityLoginService loginService) {
        this.csrf = csrf;
        this.loginService = loginService;
    }

    @GetMapping("/csrf")
    @Operation(operationId = "observabilityCsrf", summary = "签发观测门户 CSRF Token")
    public ApiResponse<ObservabilityCsrfResponse> csrf(HttpServletResponse response) {
        return ApiResponse.success(
                new ObservabilityCsrfResponse(ObservabilityCsrfService.HEADER_NAME, csrf.issue(response)));
    }

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

    @PostMapping("/logout")
    @Operation(operationId = "observabilityLogout", summary = "注销观测门户 Session")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        csrf.validate(request);
        loginService.logout(request, response);
        csrf.clear(response);
        return ApiResponse.success(true);
    }
}
