package com.cc4c.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.context.annotation.Bean;
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

/**
 * ObservabilitySecurityConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。
 */
@ManagementContextConfiguration(proxyBeanMethods = false)
public class ObservabilitySecurityConfiguration {

    /**
     * 执行 ObservabilitySecurityConfiguration 中的 observabilityAuthenticationManager 职责，并保持既有权限、事务与副作用边界。
     *
     * @param properties 调用方提供的 {@code properties} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Bean("observabilityAuthenticationManager")
    AuthenticationManager observabilityAuthenticationManager(ObservabilityProperties properties) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        InMemoryUserDetailsManager users =
                new InMemoryUserDetailsManager(User.withUsername(properties.managementUsername())
                        .password(properties.managementPasswordHash())
                        .roles("OBSERVABILITY")
                        .build());
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /**
     * 执行 ObservabilitySecurityConfiguration 中的 observabilitySecurityFilterChain 职责，并保持既有权限、事务与副作用边界。
     *
     * @param http 调用方提供的 {@code http} 值
     * @param observabilityAuthenticationManager 调用方提供的 {@code observabilityAuthenticationManager} 值
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @return 按当前声明计算、查询或转换得到的结果
     * @throws Exception 既有声明所描述的失败条件发生时抛出
     */
    @Bean
    @Order(1)
    SecurityFilterChain observabilitySecurityFilterChain(
            HttpSecurity http,
            @Qualifier("observabilityAuthenticationManager") AuthenticationManager observabilityAuthenticationManager,
            ObjectMapper objectMapper)
            throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authenticationManager(observabilityAuthenticationManager)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.HEAD,
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health/dependencies",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .hasRole("OBSERVABILITY")
                        .requestMatchers(
                                HttpMethod.HEAD,
                                "/actuator/health/dependencies",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .hasRole("OBSERVABILITY")
                        .anyRequest()
                        .denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.requireExplicitSave(false))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"cc4c-observability\"");
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(
                            response.getOutputStream(),
                            new ApiResponse<>(
                                    BusinessCode.UNAUTHORIZED.code(),
                                    false,
                                    "Observability authentication is required"));
                }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(
                            response.getOutputStream(),
                            new ApiResponse<>(BusinessCode.FORBIDDEN.code(), false, "Observability access is denied"));
                }));
        return http.build();
    }
}
