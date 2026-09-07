package com.cc4c.observability;

import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCacheProperties;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.CorrelationIds;
import com.cc4c.shared.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** ObservabilityPortalSecurityConfiguration 隔离门户权限、CORS 和无状态安全上下文。 */
@Configuration(proxyBeanMethods = false)
class ObservabilityPortalSecurityConfiguration {

    @Bean
    InitializingBean observabilityNamespaceValidator(
            ObservabilityPortalProperties portal, SecurityProperties security, BusinessCacheProperties cache) {
        return () -> {
            String businessSession = security.keyPrefix().endsWith(":security")
                    ? security.keyPrefix().substring(0, security.keyPrefix().length() - ":security".length())
                    : security.keyPrefix();
            if (portal.sessionNamespace().equals(businessSession)
                    || portal.sessionNamespace().equals(security.keyPrefix())
                    || portal.sessionNamespace().equals(cache.namespace())) {
                throw new IllegalStateException("Observability, business session and cache namespaces must differ");
            }
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain observabilityPortalSecurityFilterChain(
            HttpSecurity http,
            ObservabilityPortalProperties properties,
            ObservabilitySessionService sessions,
            ObjectMapper objectMapper)
            throws Exception {
        org.springframework.web.cors.CorsConfiguration cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowCredentials(true);
        cors.setAllowedOrigins(List.of(properties.allowedOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", ObservabilityCsrfService.HEADER_NAME));
        cors.setExposedHeaders(List.of("Retry-After", CorrelationIds.HEADER));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/observability/**", cors);

        http.securityMatcher("/observability/**")
                .cors(configuration -> configuration.configurationSource(source))
                .csrf(csrf -> csrf.disable())
                .securityContext(context -> context.requireExplicitSave(false))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/observability/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/observability/auth/csrf", "/observability/auth/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/observability/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/observability/auth/logout")
                        .hasRole("OBSERVABILITY")
                        .requestMatchers("/observability/api/**")
                        .hasRole("OBSERVABILITY")
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, 401, BusinessCode.UNAUTHORIZED, "观测会话已失效，请重新登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, 403, BusinessCode.FORBIDDEN, "无权访问观测资源")))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        http.addFilterBefore(
                new ObservabilitySessionAuthenticationFilter(sessions, objectMapper), AuthorizationFilter.class);
        return http.build();
    }

    private static void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            BusinessCode code,
            String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(code.code(), false, message));
    }
}
