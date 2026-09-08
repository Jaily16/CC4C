package com.cc4c.config;

import com.cc4c.common.CorrelationIds;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CorsConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。
 */
@Configuration
public class CorsConfiguration {

    /**
     * 执行 CorsConfiguration 中的 corsConfigurationSource 职责，并保持既有权限、事务与副作用边界。
     *
     * @param properties 调用方提供的 {@code properties} 值
     * @return 按当前声明计算、查询或转换得到的结果
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
