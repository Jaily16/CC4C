package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthenticationController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@RestController
public class AuthenticationController {
    private final CurrentActor currentActor;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    /**
     * 创建 AuthenticationController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param currentActor 调用方提供的 {@code currentActor} 值
     * @param csrfTokenRepository 由容器注入的 CookieCsrfTokenRepository 协作组件
     */
    AuthenticationController(CurrentActor currentActor, CookieCsrfTokenRepository csrfTokenRepository) {
        this.currentActor = currentActor;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * 执行 AuthenticationController 中的 csrf 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken =
                csrfTokenRepository.loadDeferredToken(request, response).get();
        return ApiResponse.success(
                new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken()));
    }

    /**
     * 执行 AuthenticationController 中的 session 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/auth/session")
    public ApiResponse<AuthSessionResponse> session() {
        return ApiResponse.success(currentActor
                .current()
                .map(actor -> new AuthSessionResponse(true, actor.role().name(), actor.id(), actor.displayName()))
                .orElseGet(() -> new AuthSessionResponse(false, null, null, null)));
    }

    /**
     * CsrfResponse 是不可变的数据载体，保持现有字段语义和序列化契约。
     *
     * @param headerName 调用方提供的 {@code headerName} 值
     * @param parameterName 调用方提供的 {@code parameterName} 值
     * @param token 调用方提供的 {@code token} 值
     */
    public record CsrfResponse(String headerName, String parameterName, String token) {}

    /**
     * AuthSessionResponse 是不可变的数据载体，保持现有字段语义和序列化契约。
     *
     * @param authenticated 调用方提供的 {@code authenticated} 值
     * @param role 调用方提供的 {@code role} 值
     * @param actorId 目标对象的稳定标识
     * @param displayName 调用方提供的 {@code displayName} 值
     */
    public record AuthSessionResponse(boolean authenticated, String role, String actorId, String displayName) {}
}
