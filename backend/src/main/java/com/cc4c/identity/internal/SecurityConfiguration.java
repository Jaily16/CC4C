package com.cc4c.identity.internal;

import com.cc4c.identity.api.Cc4cPrincipal;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.Cc4cMetrics;
import com.cc4c.shared.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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
import org.springframework.security.jackson2.SecurityJackson2Modules;
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

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        Map<String, PasswordEncoder> encoders =
                Map.of("bcrypt", new BCryptPasswordEncoder(properties.bcryptStrength()));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    AuthenticationManager authenticationManager(Cc4cAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(builder -> builder.sameSite("Lax").secure(properties.cookieSecure()));
        return repository;
    }

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

    @Bean
    <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> repository) {
        return new SpringSessionBackedSessionRegistry<>(repository);
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        RoleAwareConcurrentSessionStrategy concurrent = new RoleAwareConcurrentSessionStrategy(sessionRegistry);
        ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
        RegisterSessionAuthenticationStrategy register = new RegisterSessionAuthenticationStrategy(sessionRegistry);
        return new CompositeSessionAuthenticationStrategy(List.of(concurrent, fixation, register));
    }

    @Bean
    ConcurrentSessionFilter concurrentSessionFilter(SessionRegistry sessionRegistry, SecurityErrorWriter errorWriter) {
        return new ConcurrentSessionFilter(
                sessionRegistry,
                event -> errorWriter.write(event.getResponse(), 401, BusinessCode.UNAUTHORIZED, "会话已失效，请重新登录"));
    }

    @Bean
    FilterRegistrationBean<RedisFailureResponseFilter> redisFailureResponseFilter(SecurityErrorWriter errorWriter) {
        FilterRegistrationBean<RedisFailureResponseFilter> registration =
                new FilterRegistrationBean<>(new RedisFailureResponseFilter(errorWriter));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean(name = "springSessionDefaultRedisSerializer")
    RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper source) {
        ObjectMapper objectMapper = source.copy();
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.security.")
                .allowIfSubType("org.springframework.session.")
                .allowIfSubType("com.cc4c.identity.api.")
                .allowIfSubType("com.cc4c.identity.internal.Cc4cSessionAuthenticationToken")
                .build();
        objectMapper.activateDefaultTyping(validator, DefaultTyping.NON_FINAL);
        objectMapper.registerModules(SecurityJackson2Modules.getModules(Cc4cPrincipal.class.getClassLoader()));
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

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
                        .requestMatchers("/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/test/**")
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
