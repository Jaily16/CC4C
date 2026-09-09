package com.cc4c.security;

import com.cc4c.service.IdentityService;
import com.cc4c.support.monitoring.Cc4cMetrics;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 按请求角色查询账户并验证编码密码，产生无密码的 Session Token，同时记录认证结果指标。 */
@Component
public final class Cc4cAuthenticationProvider implements AuthenticationProvider {
    private final IdentityService identityService;
    private final PasswordEncoder passwordEncoder;
    private final Cc4cMetrics metrics;

    /**
     * 接入账户查询、密码比对和认证指标服务。
     *
     * @param identityService 按角色读取认证账户的服务
     * @param passwordEncoder 编码密码的比对器
     * @param metrics 认证结果指标记录器
     */
    @Autowired
    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder, Cc4cMetrics metrics) {
        this.identityService = identityService;
        this.passwordEncoder = passwordEncoder;
        this.metrics = metrics;
    }

    /**
     * 使用禁用指标的实现委托主构造器，保留账户认证能力。
     *
     * @param identityService 按角色读取认证账户的服务
     * @param passwordEncoder 编码密码的比对器
     */
    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder) {
        this(identityService, passwordEncoder, Cc4cMetrics.disabled());
    }

    /**
     * 验证角色对应账户及密码，成功返回带单一角色权限的会话认证；认证异常记为失败后原样抛出。
     *
     * @param authentication 待处理的认证对象
     * @return 已认证且不保存明文密码的会话 Token
     * @throws AuthenticationException 账户不存在或密码比对失败时抛出
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Cc4cAuthenticationToken request = (Cc4cAuthenticationToken) authentication;
        String role = request.requestedRole().name().toLowerCase(java.util.Locale.ROOT);
        try {
            IdentityService.AuthenticationAccount account = identityService
                    .authenticationAccount(request.requestedRole(), request.loginIdentifier())
                    .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
            String rawPassword = String.valueOf(request.getCredentials());
            if (!passwordEncoder.matches(rawPassword, account.encodedPassword())) {
                throw new BadCredentialsException("Bad credentials");
            }

            Cc4cPrincipal principal = new Cc4cPrincipal(request.requestedRole(), account.id(), account.displayName());
            Cc4cSessionAuthenticationToken authenticated = new Cc4cSessionAuthenticationToken(
                    principal,
                    List.of(new SimpleGrantedAuthority(
                            "ROLE_" + request.requestedRole().name())));
            metrics.increment("cc4c.security.authentication.attempts", "role", role, "outcome", "success");
            return authenticated;
        } catch (AuthenticationException exception) {
            metrics.increment("cc4c.security.authentication.attempts", "role", role, "outcome", "failure");
            throw exception;
        }
    }

    /**
     * 判断认证类型是否为 CC4C 登录请求 Token 或其可赋值类型。
     *
     * @param authentication 待判断的认证类型
     * @return 当前提供器支持该类型时为 true
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return Cc4cAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
