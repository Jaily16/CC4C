package com.cc4c.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定观测门户独立账号、来源、Cookie 和会话过期配置。
 *
 * @param username 观测门户用户名
 * @param passwordHash cost-12 BCrypt 密码摘要，不得记录
 * @param sessionNamespace 观测门户独立 Redis 会话命名空间
 * @param cookieSecure 是否仅通过 HTTPS 发送 Cookie
 * @param allowedOrigin 观测前端精确 HTTP(S) 来源
 * @param idleTimeout 固定为 30 分钟的空闲过期时长
 * @param absoluteTimeout 固定为 8 小时的绝对过期时长
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
     * 校验账号、cost-12 BCrypt 摘要和命名空间；固定空闲 30 分钟及绝对 8 小时过期。
     *
     * @param username 观测门户用户名
     * @param passwordHash cost-12 BCrypt 密码摘要，不得记录
     * @param sessionNamespace 观测门户独立 Redis 会话命名空间
     * @param cookieSecure 是否仅通过 HTTPS 发送 Cookie
     * @param allowedOrigin 观测前端精确 HTTP(S) 来源
     * @param idleTimeout 固定为 30 分钟的空闲过期时长
     * @param absoluteTimeout 固定为 8 小时的绝对过期时长
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
     * 将已校验的观测来源转换为 URI。
     *
     * @return 观测前端的精确来源 URI
     */
    public URI allowedOriginUri() {
        return URI.create(allowedOrigin);
    }

    /**
     * 仅接纳无路径、凭据、查询和片段的 HTTP(S) 来源；空值交由字段约束拒绝。
     *
     * @param candidate 待校验的来源字符串
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
