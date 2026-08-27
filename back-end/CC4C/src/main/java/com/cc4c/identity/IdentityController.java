package com.cc4c.identity;

import com.cc4c.identity.IdentityDtos.AdminLoginRequest;
import com.cc4c.identity.IdentityDtos.AvatarUploadResponse;
import com.cc4c.identity.IdentityDtos.ChangePasswordRequest;
import com.cc4c.identity.IdentityDtos.LoginRequest;
import com.cc4c.identity.IdentityDtos.RegisterRequest;
import com.cc4c.identity.IdentityDtos.ResetPasswordRequest;
import com.cc4c.identity.IdentityDtos.UserResponse;
import com.cc4c.identity.IdentityDtos.UserUpdateRequest;
import com.cc4c.identity.internal.IdentityService;
import com.cc4c.identity.internal.VerificationCodeService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/users")
public class IdentityController {
    private final IdentityService identityService;
    private final VerificationCodeService verificationCodeService;
    private final String saveAvatarPath;
    private final String requestAvatarPath;

    IdentityController(
            IdentityService identityService,
            VerificationCodeService verificationCodeService,
            @Value("${cc4c.save-avatar-path}") String saveAvatarPath,
            @Value("${cc4c.request-avatar-path}") String requestAvatarPath) {
        this.identityService = identityService;
        this.verificationCodeService = verificationCodeService;
        this.saveAvatarPath = saveAvatarPath;
        this.requestAvatarPath = requestAvatarPath;
    }

    @Operation(summary = "Upload a user avatar")
    @PostMapping("/uploadAvatar")
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileStorage.StoredFile stored = FileStorage.storeImage(file, saveAvatarPath, requestAvatarPath);
        return ApiResponse.success(new AvatarUploadResponse(stored.requestUrl()));
    }

    @Operation(summary = "Register a user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(identityService.register(request)));
    }

    @PutMapping("/password/forget")
    public ApiResponse<Boolean> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(identityService.resetPassword(request));
    }

    @PutMapping("/password/change")
    public ApiResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success(identityService.changePassword(request));
    }

    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        boolean loggedIn = identityService.login(request.email(), request.password());
        Cookie cookie = new Cookie("user_email", request.email());
        cookie.setMaxAge(60 * 60 * 2);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ApiResponse.success(loggedIn);
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("user_email", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ApiResponse.success(true);
    }

    @PutMapping("/update")
    public ApiResponse<Boolean> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(identityService.update(request));
    }

    @PostMapping("/email/{email}")
    public ApiResponse<String> email(
            @PathVariable @Email @Size(max = 320) String email) {
        return ApiResponse.success(verificationCodeService.send(email));
    }

    @GetMapping("/verify")
    public ApiResponse<Boolean> verify(
            @CookieValue(value = "user_email", defaultValue = "") String email) {
        if (!identityService.userExistsByEmail(email)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.success(true);
    }

    @GetMapping("/info")
    public ApiResponse<UserResponse> info(
            @CookieValue(value = "user_email", defaultValue = "") String email) {
        return ApiResponse.success(identityService.userByEmail(email));
    }
}
