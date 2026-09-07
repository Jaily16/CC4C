package com.cc4c.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** ObservabilityPortalProperties 绑定独立观测身份和 Cookie 策略，拒绝弱哈希及模糊来源。 */
@Validated
@ConfigurationProperties(prefix = "cc4c.observability.portal")
public record ObservabilityPortalProperties(
        @NotBlank String username,
        @NotBlank String passwordHash,
        @NotBlank String sessionNamespace,
        boolean cookieSecure,
        @NotBlank String allowedOrigin,
        @NotNull Duration idleTimeout,
        @NotNull Duration absoluteTimeout) {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    private static final Pattern BCRYPT_COST_12 = Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9:_-]{3,120}");

    public ObservabilityPortalProperties {
        if (username != null && !USERNAME.matcher(username).matches()) {
            throw new IllegalStateException("CC4C observability username is invalid");
        }
        if (passwordHash != null && !BCRYPT_COST_12.matcher(passwordHash).matches()) {
            throw new IllegalStateException("CC4C observability password must be a BCrypt cost-12 hash");
        }
        if (sessionNamespace != null && !NAMESPACE.matcher(sessionNamespace).matches()) {
            throw new IllegalStateException("CC4C observability session namespace is invalid");
        }
        if (!Duration.ofMinutes(30).equals(idleTimeout) || !Duration.ofHours(8).equals(absoluteTimeout)) {
            throw new IllegalStateException("CC4C observability session timeouts must remain 30 minutes and 8 hours");
        }
        validateOrigin(allowedOrigin);
    }

    public URI allowedOriginUri() {
        return URI.create(allowedOrigin);
    }

    private static void validateOrigin(String candidate) {
        if (candidate == null) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(candidate);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CC4C observability origin is invalid");
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath() == null || uri.getPath().isEmpty())) {
            throw new IllegalStateException("CC4C observability origin must be one exact HTTP origin");
        }
    }
}
