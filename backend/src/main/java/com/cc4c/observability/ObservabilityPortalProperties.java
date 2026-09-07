package com.cc4c.observability;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 承载并校验独立观测门户配置，避免调用方直接解释环境变量。
 *
 * @param username 待认证或查询的账户名
 * @param passwordHash 仅用于当前安全校验的密码或密码摘要
 * @param sessionNamespace 调用方提供的 {@code sessionNamespace} 值
 * @param cookieSecure 调用方提供的 {@code cookieSecure} 值
 * @param allowedOrigin 调用方提供的 {@code allowedOrigin} 值
 * @param idleTimeout 调用方提供的 {@code idleTimeout} 值
 * @param absoluteTimeout 调用方提供的 {@code absoluteTimeout} 值
 */
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

    /**
     * 创建 ObservabilityPortalProperties 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param username 待认证或查询的账户名
     * @param passwordHash 仅用于当前安全校验的密码或密码摘要
     * @param sessionNamespace 调用方提供的 {@code sessionNamespace} 值
     * @param cookieSecure 调用方提供的 {@code cookieSecure} 值
     * @param allowedOrigin 调用方提供的 {@code allowedOrigin} 值
     * @param idleTimeout 调用方提供的 {@code idleTimeout} 值
     * @param absoluteTimeout 调用方提供的 {@code absoluteTimeout} 值
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 当前操作产生的 URI 结果
     */
    public URI allowedOriginUri() {
        return URI.create(allowedOrigin);
    }

    /**
     * 校验观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param candidate 调用方提供的 {@code candidate} 值
     */
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
