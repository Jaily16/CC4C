package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.IdentityDtos.AvatarUploadResponse;
import com.cc4c.dto.IdentityDtos.ChangePasswordRequest;
import com.cc4c.dto.IdentityDtos.LoginRequest;
import com.cc4c.dto.IdentityDtos.RegisterRequest;
import com.cc4c.dto.IdentityDtos.ResetPasswordRequest;
import com.cc4c.dto.IdentityDtos.UserResponse;
import com.cc4c.dto.IdentityDtos.UserUpdateRequest;
import com.cc4c.dto.IdentityDtos.VerificationEmailRequest;
import com.cc4c.service.AuthenticationService;
import com.cc4c.service.IdentityService;
import com.cc4c.service.VerificationCodeService;
import com.cc4c.support.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 提供业务用户注册、资料、密码、认证及头像上传入口。 */
@Validated
@RestController
@RequestMapping("/users")
public class IdentityController {
    private final IdentityService identityService;
    private final VerificationCodeService verificationCodeService;
    private final AuthenticationService authenticationService;
    private final String saveAvatarPath;
    private final String requestAvatarPath;

    /**
     * 接入用户、验证码和认证服务，并保存头像存储及公开路径。
     *
     * @param identityService 用户及管理员资料和密码服务
     * @param verificationCodeService 按用途申请验证码发送的服务
     * @param authenticationService 业务认证与会话注销服务
     * @param saveAvatarPath 头像磁盘存储目录
     * @param requestAvatarPath 头像公开请求路径前缀
     */
    IdentityController(
            IdentityService identityService,
            VerificationCodeService verificationCodeService,
            AuthenticationService authenticationService,
            @Value("${cc4c.save-avatar-path}") String saveAvatarPath,
            @Value("${cc4c.request-avatar-path}") String requestAvatarPath) {
        this.identityService = identityService;
        this.verificationCodeService = verificationCodeService;
        this.authenticationService = authenticationService;
        this.saveAvatarPath = saveAvatarPath;
        this.requestAvatarPath = requestAvatarPath;
    }

    /**
     * 存储头像图片并返回公开请求路径；此入口本身不更新用户资料中的头像字段。
     *
     * @param file 通过 multipart 提交的图片文件
     * @return 新头像的公开路径响应
     */
    @Operation(summary = "Upload a user avatar")
    @PostMapping("/me/avatar")
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileStorage.StoredFile stored = FileStorage.storeImage(file, saveAvatarPath, requestAvatarPath);
        return ApiResponse.success(new AvatarUploadResponse(stored.requestUrl()));
    }

    /**
     * 委托用户服务验证注册资料并创建用户，成功使用 HTTP 201。
     *
     * @param request 已校验的用户注册资料和验证码
     * @return 注册结果响应
     */
    @Operation(summary = "Register a user")
    @PostMapping
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(identityService.register(request)));
    }

    /**
     * 使用邮箱验证码重置密码后注销当前业务会话。
     *
     * @param request 已校验的邮箱、验证码及新密码
     * @param servletRequest 当前 HTTP 请求，用于认证或注销会话
     * @param servletResponse 接收会话 Cookie 变更的 HTTP 响应
     * @return 密码重置结果
     */
    @PutMapping("/password/forget")
    public ApiResponse<Boolean> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        boolean changed = identityService.resetPassword(request);
        authenticationService.logout(servletRequest, servletResponse);
        return ApiResponse.success(changed);
    }

    /**
     * 使用当前密码修改用户密码后注销当前业务会话。
     *
     * @param request 已校验的当前密码及新密码
     * @param servletRequest 当前 HTTP 请求，用于认证或注销会话
     * @param servletResponse 接收会话 Cookie 变更的 HTTP 响应
     * @return 密码修改结果
     */
    @PutMapping("/me/password")
    public ApiResponse<Boolean> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        boolean changed = identityService.changePassword(request);
        authenticationService.logout(servletRequest, servletResponse);
        return ApiResponse.success(changed);
    }

    /**
     * 以邮箱和密码委托认证服务建立 USER 会话。
     *
     * @param request 已校验的用户邮箱和密码
     * @param servletRequest 当前 HTTP 请求，用于认证或注销会话
     * @param response 接收协议正文或 Cookie 的 HTTP 响应
     * @return 用户登录结果
     */
    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        return ApiResponse.success(
                authenticationService.loginUser(request.email(), request.password(), servletRequest, response));
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
     * 委托用户服务更新当前用户资料。
     *
     * @param request 已校验的当前用户可选更新资料
     * @return 资料更新结果
     */
    @PutMapping("/me")
    public ApiResponse<Boolean> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(identityService.update(request));
    }

    /**
     * 提交指定用途的验证码发送请求，使用 HTTP 202 表示已接收。
     *
     * @param request 已校验的收件邮箱和验证码用途
     * @return 验证码发送申请的接收结果
     */
    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Boolean>> email(@Valid @RequestBody VerificationEmailRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(verificationCodeService.send(request.email(), request.purpose())));
    }

    /**
     * 查询当前登录用户的资料响应。
     *
     * @return 不含密码字段的当前用户资料
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> info() {
        return ApiResponse.success(identityService.currentUser());
    }
}
