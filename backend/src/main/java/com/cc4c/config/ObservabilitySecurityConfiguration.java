package com.cc4c.config;

import com.cc4c.common.BusinessCode;
import com.cc4c.dto.ApiResponse;
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

/** 在管理上下文中配置独立 Basic 认证，隔离 Actuator 与业务身份。 */
@ManagementContextConfiguration(proxyBeanMethods = false)
public class ObservabilitySecurityConfiguration {

    /**
     * 使用配置的管理账号和 cost-12 BCrypt 摘要建立内存认证提供器。
     *
     * @param properties 对应组件的类型化配置
     * @return 仅供管理端使用的认证管理器
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
     * 公开健康探测的 GET/HEAD，保护依赖、信息和 Prometheus 端点；无状态 Basic 认证拒绝其余请求。
     *
     * @param http Spring Security HTTP 安全构建器
     * @param observabilityAuthenticationManager 管理端独立认证管理器
     * @param objectMapper 响应 JSON 映射器
     * @return 管理端 Actuator 安全链
     * @throws Exception 构建 Spring Security 过滤器链失败时抛出
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
