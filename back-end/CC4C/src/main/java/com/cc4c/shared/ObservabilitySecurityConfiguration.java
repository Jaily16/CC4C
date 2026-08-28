package com.cc4c.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@ManagementContextConfiguration(proxyBeanMethods = false)
public class ObservabilitySecurityConfiguration {

    @Bean("observabilityAuthenticationManager")
    AuthenticationManager observabilityAuthenticationManager(ObservabilityProperties properties) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager(User.withUsername(
                        properties.managementUsername())
                .password(encoder.encode(properties.managementPassword()))
                .roles("OBSERVABILITY")
                .build());
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    SecurityFilterChain observabilitySecurityFilterChain(
            HttpSecurity http,
            @Qualifier("observabilityAuthenticationManager")
            AuthenticationManager observabilityAuthenticationManager,
            ObjectMapper objectMapper) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authenticationManager(observabilityAuthenticationManager)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.HEAD,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health/dependencies",
                                "/actuator/info",
                                "/actuator/prometheus").hasRole("OBSERVABILITY")
                        .requestMatchers(HttpMethod.HEAD,
                                "/actuator/health/dependencies",
                                "/actuator/info",
                                "/actuator/prometheus").hasRole("OBSERVABILITY")
                        .anyRequest().denyAll())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(false))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                            "Basic realm=\"cc4c-observability\"");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(),
                            new ApiResponse<>(BusinessCode.UNAUTHORIZED.code(), false,
                                    "Observability authentication is required"));
                }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(),
                            new ApiResponse<>(BusinessCode.FORBIDDEN.code(), false,
                                    "Observability access is denied"));
                }));
        return http.build();
    }
}
