package com.cc4c.identity;

import com.cc4c.shared.IntValues;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;

/** IdentityDtos 表示身份、业务或交互边界上的数据传输结构。 */
public final class IdentityDtos {
    private IdentityDtos() {}

    /** RegisterRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record RegisterRequest(
            @NotBlank @Size(max = 30) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotNull @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar) {}

    /** LoginRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /** AdminLoginRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record AdminLoginRequest(
            @NotBlank @Size(max = 7) String adminId,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
                    String adminPassword) {}

    /** UserUpdateRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record UserUpdateRequest(
            @Size(min = 1, max = 30) String name,
            @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar) {}

    /** ChangePasswordRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record ChangePasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /** ResetPasswordRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /** AdministratorPasswordRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record AdministratorPasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /** VerificationEmailRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record VerificationEmailRequest(
            @NotBlank @Email @Size(max = 320) String email, @NotNull VerificationPurpose purpose) {}

    /** VerificationPurpose 表示身份、业务或交互边界上的数据传输结构。 */
    public enum VerificationPurpose {
        REGISTER,
        PASSWORD_RESET
    }

    @Schema(name = "UserResponse")
    /** UserResponse 表示身份、业务或交互边界上的数据传输结构。 */
    public record UserResponse(
            String id,
            String name,
            String email,
            Integer major,
            String avatar,
            Integer state,
            Date time,
            Integer language) {}

    /** AvatarUploadResponse 表示身份、业务或交互边界上的数据传输结构。 */
    public record AvatarUploadResponse(String requestPath) {}
}
