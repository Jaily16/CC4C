package com.cc4c.identity;

import com.cc4c.identity.IdentityDtos.AdminLoginRequest;
import com.cc4c.identity.internal.IdentityService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final IdentityService identityService;

    AdminController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/login")
    public ApiResponse<Boolean> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response) {
        boolean loggedIn = identityService.administratorLogin(request.adminId(), request.adminPassword());
        Cookie cookie = new Cookie("admin", request.adminId());
        cookie.setMaxAge(60 * 60);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ApiResponse.success(loggedIn);
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("admin", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ApiResponse.success(true);
    }

    @GetMapping("/verify")
    public ApiResponse<Boolean> verify(@CookieValue(value = "admin", defaultValue = "") String adminId) {
        if (!identityService.administratorExists(adminId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.success(true);
    }
}
