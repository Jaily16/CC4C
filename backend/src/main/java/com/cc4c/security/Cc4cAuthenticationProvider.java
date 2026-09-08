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

/**
 * Cc4cAuthenticationProvider 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
public final class Cc4cAuthenticationProvider implements AuthenticationProvider {
    private final IdentityService identityService;
    private final PasswordEncoder passwordEncoder;
    private final Cc4cMetrics metrics;

    /**
     * 创建 Cc4cAuthenticationProvider 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param identityService 由容器注入的 IdentityService 协作组件
     * @param passwordEncoder 仅用于当前安全校验的密码或密码摘要
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    @Autowired
    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder, Cc4cMetrics metrics) {
        this.identityService = identityService;
        this.passwordEncoder = passwordEncoder;
        this.metrics = metrics;
    }

    /**
     * 创建 Cc4cAuthenticationProvider 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param identityService 由容器注入的 IdentityService 协作组件
     * @param passwordEncoder 仅用于当前安全校验的密码或密码摘要
     */
    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder) {
        this(identityService, passwordEncoder, Cc4cMetrics.disabled());
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param authentication 调用方提供的 {@code authentication} 值
     * @return 当前操作产生的 Authentication 结果
     * @throws AuthenticationException 当输入、数据或依赖状态不满足当前方法约束时抛出
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
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param authentication 调用方提供的 {@code authentication} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return Cc4cAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
