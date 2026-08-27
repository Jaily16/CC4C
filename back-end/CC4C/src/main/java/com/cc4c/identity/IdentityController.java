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
    private final AuthenticationService authenticationService;
    private final String saveAvatarPath;
    private final String requestAvatarPath;

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

    @Operation(summary = "Upload a user avatar")
    @PostMapping("/me/avatar")
    public ApiResponse<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileStorage.StoredFile stored = FileStorage.storeImage(file, saveAvatarPath, requestAvatarPath);
        return ApiResponse.success(new AvatarUploadResponse(stored.requestUrl()));
    }

    @Operation(summary = "Register a user")
    @PostMapping
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(identityService.register(request)));
    }

    @PutMapping("/password/forget")
    public ApiResponse<Boolean> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        boolean changed = identityService.resetPassword(request);
        authenticationService.logout(servletRequest, servletResponse);
        return ApiResponse.success(changed);
    }

    @PutMapping("/me/password")
    public ApiResponse<Boolean> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        boolean changed = identityService.changePassword(request);
        authenticationService.logout(servletRequest, servletResponse);
        return ApiResponse.success(changed);
    }

    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        return ApiResponse.success(authenticationService.loginUser(
                request.email(), request.password(), servletRequest, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResponse.success(authenticationService.logout(request, response));
    }

    @PutMapping("/me")
    public ApiResponse<Boolean> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(identityService.update(request));
    }

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<Boolean>> email(
            @Valid @RequestBody VerificationEmailRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        verificationCodeService.send(request.email(), request.purpose())));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> info() {
        return ApiResponse.success(identityService.currentUser());
    }
}
