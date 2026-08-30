package com.cc4c.shared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cc4c.security")
/** SecurityProperties 绑定外部配置，并集中表达运行时约束和安全默认值。 */
public record SecurityProperties(
        @NotBlank @Size(min = 32) String pepper,
        boolean cookieSecure,
        @NotBlank String allowedOrigins,
        @Min(4) @Max(16) int bcryptStrength,
        boolean passwordReadinessEnabled,
        boolean redisReadinessEnabled,
        @NotBlank String keyPrefix) {
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
