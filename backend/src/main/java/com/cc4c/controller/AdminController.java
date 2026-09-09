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

/** 提供管理员登录、退出和修改密码入口；管理员业务权限由安全链约束。 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IdentityService identityService;
    private final AuthenticationService authenticationService;

    /**
     * 接入管理员资料修改和业务认证服务。
     *
     * @param identityService 用户及管理员资料和密码服务
     * @param authenticationService 业务认证与会话注销服务
     */
    AdminController(IdentityService identityService, AuthenticationService authenticationService) {
        this.identityService = identityService;
        this.authenticationService = authenticationService;
    }

    /**
     * 校验管理员登录请求，委托认证服务建立管理员会话。
     *
     * @param request 已校验的管理员编号和密码
     * @param servletRequest 当前 HTTP 请求，用于认证或注销会话
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 管理员登录结果
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
     * 委托认证服务注销当前业务会话并清理认证 Cookie。
     *
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 退出结果
     */
    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(authenticationService.logout(request, response));
    }

    /**
     * 修改管理员密码后注销当前会话，要求重新登录。
     *
     * @param request 已校验的管理员当前密码及新密码
     * @param servletRequest 当前 HTTP 请求，用于认证或注销会话
     * @param servletResponse 接收会话 Cookie 变更的 HTTP 响应
     * @return 密码修改结果
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
