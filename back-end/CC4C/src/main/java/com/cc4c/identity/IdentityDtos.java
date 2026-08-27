package com.cc4c.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import com.cc4c.shared.IntValues;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Date;

public final class IdentityDtos {
    private IdentityDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 16) String password,
            @NotNull @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 16) String password
    ) {
    }

    public record AdminLoginRequest(
            @NotBlank @Size(max = 7) String adminId,
            @NotBlank @Size(min = 4, max = 16) String adminPassword
    ) {
    }

    public record UserUpdateRequest(
            @NotNull @Positive Long id,
            @Size(min = 1, max = 30) String name,
            @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar
    ) {
    }

    public record ChangePasswordRequest(
            @NotNull @Positive Long id,
            @NotBlank @Size(min = 4, max = 16) String password,
            @NotBlank @Size(min = 4, max = 16) String newPassword
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 16) String newPassword
    ) {
    }

    @Schema(name = "UserResponse")
    public record UserResponse(
            String id,
            String name,
            String email,
            Integer major,
            String avatar,
            Integer state,
            Date time,
            Integer language
    ) {
    }

    public record AvatarUploadResponse(String requestPath) {
    }
}
