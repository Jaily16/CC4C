package com.cc4c.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定管理端认证、指标环境标签、消息采样周期及 URI 基数限制。
 *
 * @param enabled 是否启用该配置对应的能力
 * @param environment 低基数环境标签
 * @param managementUsername 管理端 Basic 认证用户名
 * @param managementPasswordHash 管理端 cost-12 BCrypt 密码摘要
 * @param messagingSampleInterval 消息状态指标采样间隔
 * @param maxHttpUriTags 最多接纳的不同 HTTP URI 标签数量
 */
@Validated
@ConfigurationProperties(prefix = "cc4c.observability")
public record ObservabilityProperties(
        boolean enabled,
        @NotBlank String environment,
        @NotBlank String managementUsername,
        @NotBlank String managementPasswordHash,
        @NotNull Duration messagingSampleInterval,
        int maxHttpUriTags) {
    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9-]{2,32}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");

    /**
     * 校验管理账号及 cost-12 BCrypt 摘要、正数采样周期和 10 至 500 的 URI 标签上限。
     *
     * @param enabled 是否启用该配置对应的能力
     * @param environment 低基数环境标签
     * @param managementUsername 管理端 Basic 认证用户名
     * @param managementPasswordHash 管理端 cost-12 BCrypt 密码摘要
     * @param messagingSampleInterval 消息状态指标采样间隔
     * @param maxHttpUriTags 最多接纳的不同 HTTP URI 标签数量
     */
    public ObservabilityProperties {
        if (environment != null && !ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalStateException("CC4C observability environment is invalid");
        }
        if (managementUsername != null && !USERNAME.matcher(managementUsername).matches()) {
            throw new IllegalStateException("CC4C management username is invalid");
        }
        if (managementPasswordHash != null && !managementPasswordHash.matches("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$")) {
            throw new IllegalStateException("CC4C management password must be a BCrypt cost-12 hash");
        }
        if (messagingSampleInterval != null
                && (messagingSampleInterval.isNegative() || messagingSampleInterval.isZero())) {
            throw new IllegalStateException("CC4C messaging sample interval must be positive");
        }
        if (maxHttpUriTags < 10 || maxHttpUriTags > 500) {
            throw new IllegalStateException("CC4C maximum HTTP URI tag count must be between 10 and 500");
        }
    }
}
