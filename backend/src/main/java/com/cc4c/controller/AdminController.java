package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.IdentityDtos.AdminLoginRequest;
import com.cc4c.dto.IdentityDtos.AdministratorPasswordRequest;
import com.cc4c.service.AuthenticationService;
import com.cc4c.service.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AdminController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IdentityService identityService;
    private final AuthenticationService authenticationService;

    /**
     * 创建 AdminController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param identityService 由容器注入的 IdentityService 协作组件
     * @param authenticationService 由容器注入的 AuthenticationService 协作组件
     */
    AdminController(IdentityService identityService, AuthenticationService authenticationService) {
        this.identityService = identityService;
        this.authenticationService = authenticationService;
    }

    /**
     * 执行 AdminController 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @param servletRequest 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        return ApiResponse.success(authenticationService.loginAdministrator(
                request.adminId(), request.adminPassword(), servletRequest, response));
    }

    /**
     * 执行 AdminController 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param request 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authenticationService.logout(request, response));
    }

    /**
     * 变更 AdminController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @param servletRequest 当前 HTTP 请求，用于读取来源与请求上下文
     * @param servletResponse 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/password")
    public ApiResponse<Boolean> changePassword(
            @Valid @RequestBody AdministratorPasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        boolean changed = identityService.changeAdministratorPassword(request);
        authenticationService.logout(servletRequest, servletResponse);
        return ApiResponse.success(changed);
    }
}
