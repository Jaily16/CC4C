package com.cc4c.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
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
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
