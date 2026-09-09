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

/** 装配观测门户独立会话、CORS 和角色校验链，不复用业务 HttpSession 登录。 */
@Configuration(proxyBeanMethods = false)
class ObservabilityPortalSecurityConfiguration {

    /**
     * 创建启动校验器，拒绝观测会话与业务 Session、安全键或缓存命名空间相同。
     *
     * @param portal 观测门户会话配置
     * @param security 业务安全键配置
     * @param cache 业务缓存命名空间配置
     * @return 容器初始化时执行的命名空间校验器
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
     * 匹配观测路径并使用自定义会话过滤器；只公开登录和会话探测入口，其余已列接口要求观测角色。
     *
     * @param http Spring Security HTTP 安全构建器
     * @param properties 对应组件的类型化配置
     * @param sessions 观测会话查询服务
     * @param objectMapper 响应 JSON 映射器
     * @return 顺序为 1 的观测门户安全链
     * @throws Exception 构建 Spring Security 过滤器链失败时抛出
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
     * 向观测请求写入指定状态码和 JSON 错误响应，并禁止缓存。
     *
     * @param response 接收错误状态和正文的 HTTP 响应
     * @param objectMapper 响应 JSON 映射器
     * @param status HTTP 响应状态码
     * @param code 业务错误码
     * @param message 可向客户端展示的错误提示
     * @throws java.io.IOException 响应输出流写入失败时抛出
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
