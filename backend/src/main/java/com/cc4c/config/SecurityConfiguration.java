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

/** 装配业务密码、Session、CSRF 和角色授权，使用顺序为 2 的安全链承接业务请求。 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    /**
     * 以配置的 BCrypt 强度构造带 bcrypt 编码标识的密码编码器。
     *
     * @param properties 对应组件的类型化配置
     * @return 只注册 bcrypt 算法的委托编码器
     */
    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        Map<String, PasswordEncoder> encoders =
                Map.of("bcrypt", new BCryptPasswordEncoder(properties.bcryptStrength()));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /**
     * 使用 CC4C 认证提供器处理用户和管理员认证。
     *
     * @param provider 业务用户及管理员认证提供器
     * @return 业务认证管理器
     */
    @Bean
    AuthenticationManager authenticationManager(Cc4cAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    /**
     * 通过 HttpSession 保存和读取业务安全上下文。
     *
     * @return 业务安全上下文仓库
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 配置可由前端读取的 XSRF-TOKEN Cookie，并通过 X-XSRF-TOKEN 请求头校验。
     *
     * @param properties 对应组件的类型化配置
     * @return SameSite=Lax 的业务 CSRF 仓库
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
     * 配置根路径的 HttpOnly 业务 Session Cookie，使用 SameSite=Lax 和两小时 Cookie 有效期。
     *
     * @param properties 对应组件的类型化配置
     * @return 业务 Session Cookie 序列化器
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
     * 基于 Spring Session 索引查询身份名关联的会话。
     *
     * @param <S> 持久化会话类型
     * @param repository 按身份名建立索引的会话仓库
     * @return 共享持久化会话索引的注册表
     */
    @Bean
    <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> repository) {
        return new SpringSessionBackedSessionRegistry<>(repository);
    }

    /**
     * 依次执行角色并发限制、会话 ID 更换和会话注册。
     *
     * @param sessionRegistry 业务共享会话注册表
     * @return 登录成功时使用的组合会话策略
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        RoleAwareConcurrentSessionStrategy concurrent = new RoleAwareConcurrentSessionStrategy(sessionRegistry);
        ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
        RegisterSessionAuthenticationStrategy register = new RegisterSessionAuthenticationStrategy(sessionRegistry);
        return new CompositeSessionAuthenticationStrategy(List.of(concurrent, fixation, register));
    }

    /**
     * 检测已过期的并发会话并返回 401 JSON 提示。
     *
     * @param sessionRegistry 业务共享会话注册表
     * @param errorWriter 统一安全错误响应写入器
     * @return 会话失效响应过滤器
     */
    @Bean
    ConcurrentSessionFilter concurrentSessionFilter(SessionRegistry sessionRegistry, SecurityErrorWriter errorWriter) {
        return new ConcurrentSessionFilter(
                sessionRegistry,
                event -> errorWriter.write(event.getResponse(), 401, BusinessCode.UNAUTHORIZED, "会话已失效，请重新登录"));
    }

    /**
     * 将 Redis 故障转换过滤器注册在靠前的 Servlet 过滤器位置。
     *
     * @param errorWriter 统一安全错误响应写入器
     * @return 带明确执行顺序的过滤器注册项
     */
    @Bean
    FilterRegistrationBean<RedisFailureResponseFilter> redisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        FilterRegistrationBean<RedisFailureResponseFilter> registration =
                new FilterRegistrationBean<>(new RedisFailureResponseFilter(errorWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 保留 Spring Session 默认序列化器 Bean 名，使用受限类型及旧身份别名兼容的 JSON 实现。
     *
     * @param source 用于复制配置的应用 JSON 映射器
     * @return 业务 Session JSON 序列化器
     */
    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper source) {
        return new SessionJsonRedisSerializer(source);
    }

    /**
     * 按请求路径和方法区分公开读取、USER 与 ADMIN 权限，显式保存会话并启用业务 CSRF。
     *
     * @param http Spring Security HTTP 安全构建器
     * @param csrfTokenRepository 业务 CSRF Cookie 与请求头仓库
     * @param securityContextRepository 业务 HttpSession 安全上下文仓库
     * @param legacyCookieCleanupFilter 过期身份 Cookie 清理过滤器
     * @param concurrentSessionFilter 并发会话失效检测过滤器
     * @param errorWriter 统一安全错误响应写入器
     * @param metrics 统一指标记录器
     * @return 包含旧 Cookie 清理及并发会话检测的业务安全链
     * @throws Exception 构建 Spring Security 过滤器链失败时抛出
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
     * 从当前认证的权限集合提取低基数指标角色标签，优先管理员，其次用户。
     *
     * @return admin、user 或 anonymous
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
