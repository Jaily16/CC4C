package com.cc4c.dto;

import com.cc4c.common.IntValues;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;

/**
 * IdentityDtos 表示身份、业务或交互边界上的数据传输结构。
 */
public final class IdentityDtos {
    /**
     * 创建 IdentityDtos 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     */
    private IdentityDtos() {}

    /**
     * RegisterRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param email 调用方提供的 {@code email} 值
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param verificationCode 调用方提供的 {@code verificationCode} 值
     * @param major 调用方提供的 {@code major} 值
     * @param language 调用方提供的 {@code language} 值
     * @param avatar 调用方提供的 {@code avatar} 值
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotNull @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar) {}

    /**
     * LoginRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /**
     * AdminLoginRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param adminId 目标对象的稳定标识
     * @param adminPassword 调用方提供的 {@code adminPassword} 值
     */
    public record AdminLoginRequest(
            @NotBlank @Size(max = 7) String adminId,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
                    String adminPassword) {}

    /**
     * UserUpdateRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param major 调用方提供的 {@code major} 值
     * @param language 调用方提供的 {@code language} 值
     * @param avatar 调用方提供的 {@code avatar} 值
     */
    public record UserUpdateRequest(
            @Size(min = 1, max = 30) String name,
            @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar) {}

    /**
     * ChangePasswordRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param newPassword 调用方提供的 {@code newPassword} 值
     */
    public record ChangePasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * ResetPasswordRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param verificationCode 调用方提供的 {@code verificationCode} 值
     * @param newPassword 调用方提供的 {@code newPassword} 值
     */
    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * AdministratorPasswordRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param password 调用方提供的敏感凭据，处理期间不得写入日志
     * @param newPassword 调用方提供的 {@code newPassword} 值
     */
    public record AdministratorPasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * VerificationEmailRequest 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param email 调用方提供的 {@code email} 值
     * @param purpose 调用方提供的 {@code purpose} 值
     */
    public record VerificationEmailRequest(
            @NotBlank @Email @Size(max = 320) String email, @NotNull VerificationPurpose purpose) {}

    /**
     * VerificationPurpose 表示身份、业务或交互边界上的数据传输结构。
     */
    public enum VerificationPurpose {
        REGISTER,
        PASSWORD_RESET
    }

    /**
     * UserResponse 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param id 目标对象的稳定标识
     * @param name 调用方提供的 {@code name} 值
     * @param email 调用方提供的 {@code email} 值
     * @param major 调用方提供的 {@code major} 值
     * @param avatar 调用方提供的 {@code avatar} 值
     * @param state 调用方提供的 {@code state} 值
     * @param time 调用方提供的 {@code time} 值
     * @param language 调用方提供的 {@code language} 值
     */
    @Schema(name = "UserResponse")
    public record UserResponse(
            String id,
            String name,
            String email,
            Integer major,
            String avatar,
            Integer state,
            Date time,
            Integer language) {}

    /**
     * AvatarUploadResponse 表示身份、业务或交互边界上的数据传输结构。
     *
     * @param requestPath 调用方提供的 {@code requestPath} 值
     */
    public record AvatarUploadResponse(String requestPath) {}
}
