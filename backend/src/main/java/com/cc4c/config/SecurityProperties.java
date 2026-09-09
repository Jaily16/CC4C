package com.cc4c.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定业务密码强度、摘要 pepper、CORS 来源及 Redis 安全键前缀。
 *
 * @param pepper 安全键摘要使用的秘密 pepper，不得记录
 * @param cookieSecure 是否仅通过 HTTPS 发送 Cookie
 * @param allowedOrigins 逗号分隔的业务前端精确来源
 * @param bcryptStrength BCrypt 工作因子，范围 4 至 16
 * @param keyPrefix 业务限流等安全键的 Redis 前缀
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
     * 按逗号拆分允许来源并去除空项；没有任何来源时拒绝配置。
     *
     * @return 去除首尾空白后的来源列表
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
