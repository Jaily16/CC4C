package com.cc4c.identity;

import com.cc4c.identity.IdentityDtos.AvatarUploadResponse;
import com.cc4c.identity.IdentityDtos.ChangePasswordRequest;
import com.cc4c.identity.IdentityDtos.LoginRequest;
import com.cc4c.identity.IdentityDtos.RegisterRequest;
import com.cc4c.identity.IdentityDtos.ResetPasswordRequest;
import com.cc4c.identity.IdentityDtos.UserResponse;
import com.cc4c.identity.IdentityDtos.UserUpdateRequest;
import com.cc4c.identity.IdentityDtos.VerificationEmailRequest;
import com.cc4c.identity.internal.AuthenticationService;
import com.cc4c.identity.internal.IdentityService;
import com.cc4c.identity.internal.VerificationCodeService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.FileStorage;
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

/**
 * IdentityController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
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
     * 创建 IdentityController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param identityService 由容器注入的 IdentityService 协作组件
     * @param verificationCodeService 由容器注入的 VerificationCodeService 协作组件
     * @param authenticationService 由容器注入的 AuthenticationService 协作组件
     * @param saveAvatarPath 调用方提供的 {@code saveAvatarPath} 值
     * @param requestAvatarPath 调用方提供的 {@code requestAvatarPath} 值
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
     * 执行 IdentityController 中的 uploadAvatar 职责，并保持既有权限、事务与副作用边界。
     *
     * @param file 调用方提供的 {@code file} 值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @Operation(summary = "Upload a user avatar")
    @PostMapping("/me/avatar")
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileStorage.StoredFile stored = FileStorage.storeImage(file, saveAvatarPath, requestAvatarPath);
        return ApiResponse.success(new AvatarUploadResponse(stored.requestUrl()));
    }

    /**
     * 变更 IdentityController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @Operation(summary = "Register a user")
    @PostMapping
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(identityService.register(request)));
    }

    /**
     * 变更 IdentityController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @param servletRequest 当前 HTTP 请求，用于读取来源与请求上下文
     * @param servletResponse 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 变更 IdentityController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @param servletRequest 当前 HTTP 请求，用于读取来源与请求上下文
     * @param servletResponse 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 IdentityController 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @param servletRequest 当前 HTTP 请求，用于读取来源与请求上下文
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        return ApiResponse.success(
                authenticationService.loginUser(request.email(), request.password(), servletRequest, response));
    }

    /**
     * 执行 IdentityController 的身份会话流程，并保持 Cookie、CSRF 与限流边界。
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
     * 变更 IdentityController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/me")
    public ApiResponse<Boolean> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(identityService.update(request));
    }

    /**
     * 执行 IdentityController 中的 email 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Boolean>> email(@Valid @RequestBody VerificationEmailRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(verificationCodeService.send(request.email(), request.purpose())));
    }

    /**
     * 执行 IdentityController 中的 info 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> info() {
        return ApiResponse.success(identityService.currentUser());
    }
}
