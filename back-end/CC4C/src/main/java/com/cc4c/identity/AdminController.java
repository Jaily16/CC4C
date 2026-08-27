package com.cc4c.identity;

import com.cc4c.identity.IdentityDtos.AdminLoginRequest;
import com.cc4c.identity.IdentityDtos.AdministratorPasswordRequest;
import com.cc4c.identity.internal.AuthenticationService;
import com.cc4c.identity.internal.IdentityService;
import com.cc4c.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IdentityService identityService;
    private final AuthenticationService authenticationService;

    AdminController(IdentityService identityService, AuthenticationService authenticationService) {
        this.identityService = identityService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        return ApiResponse.success(authenticationService.loginAdministrator(
                request.adminId(), request.adminPassword(), servletRequest, response));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResponse.success(authenticationService.logout(request, response));
    }

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
