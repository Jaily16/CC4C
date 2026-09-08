package com.cc4c.config;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.CorrelationIds;
import com.cc4c.dto.ApiResponse;
import com.cc4c.security.ObservabilityCsrfService;
import com.cc4c.security.ObservabilitySessionAuthenticationFilter;
import com.cc4c.security.ObservabilitySessionService;
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

/**
 * 装配独立观测门户运行组件，并集中声明安全或基础设施策略。
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityPortalSecurityConfiguration {

    /**
     * 创建并配置 ObservabilityPortalSecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param portal 由容器注入的 ObservabilityPortalProperties 协作组件
     * @param security 由容器注入的 SecurityProperties 协作组件
     * @param cache 由容器注入的 BusinessCacheProperties 协作组件
     * @return 当前操作产生的 InitializingBean 结果
     */
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

    /**
     * 创建并配置 ObservabilityPortalSecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param http 调用方提供的 {@code http} 值
     * @param properties 由容器注入的 ObservabilityPortalProperties 协作组件
     * @param sessions 由容器注入的 ObservabilitySessionService 协作组件
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @return 当前操作产生的 SecurityFilterChain 结果
     * @throws Exception 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param status 当前对象或流程的有限状态
     * @param code 调用方提供的 {@code code} 值
     * @param message 当前处理的消息或用户提示
     * @throws java.io.IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
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
