package com.cc4c.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ObservabilityProperties 绑定外部配置，并集中表达运行时约束和安全默认值。
 *
 * @param enabled 是否启用对应受控能力
 * @param environment 调用方提供的 {@code environment} 值
 * @param managementUsername 调用方提供的 {@code managementUsername} 值
 * @param managementPasswordHash 调用方提供的 {@code managementPasswordHash} 值
 * @param messagingSampleInterval 调用方提供的 {@code messagingSampleInterval} 值
 * @param maxHttpUriTags 调用方提供的 {@code maxHttpUriTags} 值
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
     * 创建 ObservabilityProperties 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param enabled 是否启用对应受控能力
     * @param environment 调用方提供的 {@code environment} 值
     * @param managementUsername 调用方提供的 {@code managementUsername} 值
     * @param managementPasswordHash 调用方提供的 {@code managementPasswordHash} 值
     * @param messagingSampleInterval 调用方提供的 {@code messagingSampleInterval} 值
     * @param maxHttpUriTags 调用方提供的 {@code maxHttpUriTags} 值
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
