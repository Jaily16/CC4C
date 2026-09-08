package com.cc4c.config;

import com.cc4c.common.BusinessCode;
import com.cc4c.security.Cc4cAuthenticationProvider;
import com.cc4c.security.LegacyCookieCleanupFilter;
import com.cc4c.security.RedisFailureResponseFilter;
import com.cc4c.security.RoleAwareConcurrentSessionStrategy;
import com.cc4c.security.SecurityErrorWriter;
import com.cc4c.security.SessionJsonRedisSerializer;
import com.cc4c.security.SpaCsrfTokenRequestHandler;
import com.cc4c.support.monitoring.Cc4cMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 装配身份认证运行组件，并集中声明安全或基础设施策略。
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param properties 由容器注入的 SecurityProperties 协作组件
     * @return 当前操作产生的 PasswordEncoder 结果
     */
    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        Map<String, PasswordEncoder> encoders =
                Map.of("bcrypt", new BCryptPasswordEncoder(properties.bcryptStrength()));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param provider 调用方提供的 {@code provider} 值
     * @return 当前操作产生的 AuthenticationManager 结果
     */
    @Bean
    AuthenticationManager authenticationManager(Cc4cAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @return 当前操作产生的 SecurityContextRepository 结果
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param properties 由容器注入的 SecurityProperties 协作组件
     * @return 当前操作产生的 CookieCsrfTokenRepository 结果
     */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(builder -> builder.sameSite("Lax").secure(properties.cookieSecure()));
        return repository;
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param properties 由容器注入的 SecurityProperties 协作组件
     * @return 当前操作产生的 DefaultCookieSerializer 结果
     */
    @Bean
    DefaultCookieSerializer cookieSerializer(SecurityProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("CC4C_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(properties.cookieSecure());
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(2 * 60 * 60);
        return serializer;
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param <S> 方法使用的类型参数
     * @param repository 调用方提供的 {@code repository} 值
     * @return 当前操作产生的 SessionRegistry 结果
     */
    @Bean
    <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> repository) {
        return new SpringSessionBackedSessionRegistry<>(repository);
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param sessionRegistry 调用方提供的 {@code sessionRegistry} 值
     * @return 当前操作产生的 SessionAuthenticationStrategy 结果
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        RoleAwareConcurrentSessionStrategy concurrent = new RoleAwareConcurrentSessionStrategy(sessionRegistry);
        ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
        RegisterSessionAuthenticationStrategy register = new RegisterSessionAuthenticationStrategy(sessionRegistry);
        return new CompositeSessionAuthenticationStrategy(List.of(concurrent, fixation, register));
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param sessionRegistry 调用方提供的 {@code sessionRegistry} 值
     * @param errorWriter 调用方提供的 {@code errorWriter} 值
     * @return 当前操作产生的 ConcurrentSessionFilter 结果
     */
    @Bean
    ConcurrentSessionFilter concurrentSessionFilter(SessionRegistry sessionRegistry, SecurityErrorWriter errorWriter) {
        return new ConcurrentSessionFilter(
                sessionRegistry,
                event -> errorWriter.write(event.getResponse(), 401, BusinessCode.UNAUTHORIZED, "会话已失效，请重新登录"));
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param errorWriter 调用方提供的 {@code errorWriter} 值
     * @return 当前操作产生的 FilterRegistrationBean<RedisFailureResponseFilter> 结果
     */
    @Bean
    FilterRegistrationBean<RedisFailureResponseFilter> redisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        FilterRegistrationBean<RedisFailureResponseFilter> registration =
                new FilterRegistrationBean<>(new RedisFailureResponseFilter(errorWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param source 由容器注入的 ObjectMapper 协作组件
     * @return 当前操作产生的 RedisSerializer<Object> 结果
     */
    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper source) {
        return new SessionJsonRedisSerializer(source);
    }

    /**
     * 创建并配置 SecurityConfiguration 所需的 Spring Bean，集中维护运行策略。
     *
     * @param http 调用方提供的 {@code http} 值
     * @param csrfTokenRepository 当前协议使用且不得记录的安全令牌
     * @param securityContextRepository 由容器注入的 SecurityContextRepository 协作组件
     * @param legacyCookieCleanupFilter 调用方提供的 {@code legacyCookieCleanupFilter} 值
     * @param concurrentSessionFilter 调用方提供的 {@code concurrentSessionFilter} 值
     * @param errorWriter 调用方提供的 {@code errorWriter} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     * @return 当前操作产生的 SecurityFilterChain 结果
     * @throws Exception 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            LegacyCookieCleanupFilter legacyCookieCleanupFilter,
            ConcurrentSessionFilter concurrentSessionFilter,
            SecurityErrorWriter errorWriter,
            Cc4cMetrics metrics)
            throws Exception {
        http.cors(cors -> {})
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/csrf", "/auth/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/users", "/users/login", "/users/email")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/users/password/forget")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/courses/star", "/courses/star/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/courses/star/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/courses/star/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/courses/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/courses/module", "/courses/add")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/blogs/examine", "/blogs/examine/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/blogs/approve/**", "/blogs/deny/**")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.GET, "/blogs/myBlogs", "/blogs/draft", "/blogs/collect", "/blogs/collect/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/blogs/uploadImg")
                        .hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/blogs/submit", "/blogs/collect/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.PUT, "/blogs/draft")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/blogs/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.PUT, "/blogs/click/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/blogs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/comments/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/comments/**")
                        .hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/comments/**")
                        .hasRole("USER")
                        .requestMatchers("/users/**")
                        .hasRole("USER")
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            metrics.increment(
                                    "cc4c.security.authorization.denials",
                                    "role",
                                    "anonymous",
                                    "reason",
                                    "unauthenticated");
                            errorWriter.write(response, 401, BusinessCode.UNAUTHORIZED, "请先登录");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            metrics.increment(
                                    "cc4c.security.authorization.denials",
                                    "role",
                                    currentRole(),
                                    "reason",
                                    exception instanceof CsrfException ? "csrf" : "access");
                            errorWriter.write(
                                    response,
                                    403,
                                    BusinessCode.FORBIDDEN,
                                    exception instanceof CsrfException ? "CSRF 验证失败" : "无权执行此操作");
                        }))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        http.addFilterBefore(legacyCookieCleanupFilter, SecurityContextHolderFilter.class);
        http.addFilterAfter(concurrentSessionFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    private static String currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return "admin";
        }
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()))) {
            return "user";
        }
        return "anonymous";
    }
}
