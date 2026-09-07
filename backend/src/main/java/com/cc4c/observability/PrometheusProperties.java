package com.cc4c.observability;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** PrometheusProperties 约束服务端唯一 Prometheus 上游，禁止在地址中夹带凭据或查询。 */
@Validated
@ConfigurationProperties(prefix = "cc4c.observability.prometheus")
public record PrometheusProperties(@NotNull URI url, String username, String password) {

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

    public boolean authenticated() {
        return username != null && !username.isBlank();
    }
}
