package com.cc4c.config;

import com.cc4c.common.CorrelationIds;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 为业务接口配置允许携带 Cookie 的精确来源 CORS 策略。 */
@Configuration
public class CorsConfiguration {

    /**
     * 拒绝带通配符的来源，允许业务读写方法及 CSRF 请求头，并暴露重试和关联 ID 响应头。
     *
     * @param properties 对应组件的类型化配置
     * @return 应用于所有业务路径的 CORS 配置源
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        List<String> origins = properties.allowedOriginList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("Credentialed CORS origins must not contain wildcards");
        }

        org.springframework.web.cors.CorsConfiguration configuration =
                new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Retry-After", CorrelationIds.HEADER));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
