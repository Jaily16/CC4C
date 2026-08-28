package com.cc4c.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.regex.Pattern;

@Validated
@ConfigurationProperties(prefix = "cc4c.observability")
public record ObservabilityProperties(
        boolean enabled,
        @NotBlank String environment,
        @NotBlank String managementUsername,
        @NotBlank String managementPassword,
        @NotNull Duration messagingSampleInterval,
        int maxHttpUriTags
) {
    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9-]{2,32}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");

    public ObservabilityProperties {
        if (environment != null && !ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalStateException("CC4C observability environment is invalid");
        }
        if (managementUsername != null && !USERNAME.matcher(managementUsername).matches()) {
            throw new IllegalStateException("CC4C management username is invalid");
        }
        if (enabled && managementPassword != null && managementPassword.length() < 24) {
            throw new IllegalStateException("CC4C management password must contain at least 24 characters");
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
