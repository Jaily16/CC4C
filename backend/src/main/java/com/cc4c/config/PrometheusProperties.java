package com.cc4c.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 承载并校验独立观测门户配置，避免调用方直接解释环境变量。
 *
 * @param url 调用方提供的 {@code url} 值
 * @param username 待认证或查询的账户名
 * @param password 仅用于当前安全校验的密码或密码摘要
 */
@Validated
@ConfigurationProperties(prefix = "cc4c.observability.prometheus")
public record PrometheusProperties(@NotNull URI url, String username, String password) {

    /**
     * 创建 PrometheusProperties 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param url 调用方提供的 {@code url} 值
     * @param username 待认证或查询的账户名
     * @param password 仅用于当前安全校验的密码或密码摘要
     */
    public PrometheusProperties {
        if (url != null
                && (!("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                        || url.getHost() == null
                        || url.getUserInfo() != null
                        || url.getQuery() != null
                        || url.getFragment() != null)) {
            throw new IllegalStateException("CC4C Prometheus URL is invalid");
        }
        boolean hasUsername = username != null && !username.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (hasUsername != hasPassword) {
            throw new IllegalStateException("CC4C Prometheus credentials must be configured as a pair");
        }
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    public boolean authenticated() {
        return username != null && !username.isBlank();
    }
}
