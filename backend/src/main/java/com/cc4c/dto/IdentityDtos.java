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

/** 集中声明业务用户与管理员的认证、资料和密码请求，以及不含密码的用户响应。 */
public final class IdentityDtos {
    /** 仅作为嵌套 DTO 的命名容器，禁止外部实例化。 */
    private IdentityDtos() {}

    /**
     * 用户注册资料与六位邮箱验证码；密码字段只用于请求，不进入响应模型。
     *
     * @param name 用户昵称
     * @param email 账户邮箱地址
     * @param password 当前认证使用的明文密码，不得记录
     * @param verificationCode 六位数字邮箱验证码
     * @param major 用户专业分类编码（-1、0、1）
     * @param language 用户偏好语言 ID
     * @param avatar 头像请求路径
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
     * 业务用户邮箱与登录密码；兼容既有账户的 4 至 64 字符密码输入。
     *
     * @param email 账户邮箱地址
     * @param password 当前认证使用的明文密码，不得记录
     */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /**
     * 管理员编号与密码登录请求；编号非空且最多七个字符。
     *
     * @param adminId 管理员编号
     * @param adminPassword 管理员登录密码，不得记录
     */
    public record AdminLoginRequest(
            @NotBlank @Size(max = 7) String adminId,
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
                    String adminPassword) {}

    /**
     * 用户资料的可选更新字段；只声明格式校验，字段合并由用户服务负责。
     *
     * @param name 用户昵称
     * @param major 用户专业分类编码（-1、0、1）
     * @param language 用户偏好语言 ID
     * @param avatar 头像请求路径
     */
    public record UserUpdateRequest(
            @Size(min = 1, max = 30) String name,
            @IntValues({-1, 0, 1}) Integer major,
            @Positive Integer language,
            @Size(max = 260) String avatar) {}

    /**
     * 使用当前密码验证身份并指定新密码的请求。
     *
     * @param password 当前认证使用的明文密码，不得记录
     * @param newPassword 8 至 64 字符的新密码，不得记录
     */
    public record ChangePasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * 通过邮箱和六位验证码指定新密码的重置请求。
     *
     * @param email 账户邮箱地址
     * @param verificationCode 六位数字邮箱验证码
     * @param newPassword 8 至 64 字符的新密码，不得记录
     */
    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String verificationCode,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * 管理员使用当前密码验证身份并指定新密码的请求。
     *
     * @param password 当前认证使用的明文密码，不得记录
     * @param newPassword 8 至 64 字符的新密码，不得记录
     */
    public record AdministratorPasswordRequest(
            @NotBlank @Size(min = 4, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @NotBlank @Size(min = 8, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {}

    /**
     * 申请向指定邮箱发送注册或密码重置验证码。
     *
     * @param email 账户邮箱地址
     * @param purpose 验证码用途
     */
    public record VerificationEmailRequest(
            @NotBlank @Email @Size(max = 320) String email, @NotNull VerificationPurpose purpose) {}

    /** 区分注册与密码重置验证码，防止两个用途混用。 */
    public enum VerificationPurpose {
        REGISTER,
        PASSWORD_RESET
    }

    /**
     * 用户资料响应；包含邮箱和账户状态，不包含密码或密码摘要。
     *
     * @param id 用户 ID 的字符串表示
     * @param name 用户昵称
     * @param email 账户邮箱地址
     * @param major 用户专业分类编码（-1、0、1）
     * @param avatar 头像请求路径
     * @param state 用户账户状态
     * @param time 用户创建时间
     * @param language 用户偏好语言 ID
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
     * 返回头像上传后的公开请求路径，不暴露磁盘存储位置。
     *
     * @param requestPath 头像公开请求路径
     */
    public record AvatarUploadResponse(String requestPath) {}
}
