package com.cc4c.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SecurityProperties 绑定外部配置，并集中表达运行时约束和安全默认值。
 *
 * @param pepper 调用方提供的 {@code pepper} 值
 * @param cookieSecure 调用方提供的 {@code cookieSecure} 值
 * @param allowedOrigins 调用方提供的 {@code allowedOrigins} 值
 * @param bcryptStrength 调用方提供的 {@code bcryptStrength} 值
 * @param keyPrefix 调用方提供的 {@code keyPrefix} 值
 */
@Validated
@ConfigurationProperties(prefix = "cc4c.security")
public record SecurityProperties(
        @NotBlank @Size(min = 32) String pepper,
        boolean cookieSecure,
        @NotBlank String allowedOrigins,
        @Min(4) @Max(16) int bcryptStrength,
        @NotBlank String keyPrefix) {
    /**
     * 执行 SecurityProperties 中的 allowedOriginList 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public List<String> allowedOriginList() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("At least one CORS origin is required");
        }
        return origins;
    }
}
