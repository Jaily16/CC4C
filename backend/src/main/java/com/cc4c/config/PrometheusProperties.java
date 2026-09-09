package com.cc4c.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定观测门户访问 Prometheus 的地址和可选成对凭据。
 *
 * @param url Prometheus 的 HTTP(S) 地址
 * @param username 可选 Prometheus 认证用户名
 * @param password Prometheus 认证密码，不得记录
 */
@Validated
@ConfigurationProperties(prefix = "cc4c.observability.prometheus")
public record PrometheusProperties(@NotNull URI url, String username, String password) {

    /**
     * 拒绝非法 HTTP(S) 地址及只配置一半的用户名密码；地址不得内嵌凭据、查询或片段。
     *
     * @param url Prometheus 的 HTTP(S) 地址
     * @param username 可选 Prometheus 认证用户名
     * @param password Prometheus 认证密码，不得记录
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
     * 检查是否配置非空用户名；成对凭据的完整性已由构造校验保证。
     *
     * @return 是否需要向 Prometheus 使用认证
     */
    public boolean authenticated() {
        return username != null && !username.isBlank();
    }
}
