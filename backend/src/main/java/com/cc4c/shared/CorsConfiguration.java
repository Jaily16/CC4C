package com.cc4c.shared;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
/** CorsConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。 */
public class CorsConfiguration {

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
