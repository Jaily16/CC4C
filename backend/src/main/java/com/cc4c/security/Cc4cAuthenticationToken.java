package com.cc4c.security;

import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Cc4cAuthenticationToken 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
public final class Cc4cAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AccountRole requestedRole;
    private final String loginIdentifier;
    private Object credentials;
    private Cc4cPrincipal principal;

    /**
     * 创建 Cc4cAuthenticationToken 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param requestedRole 调用方提供的 {@code requestedRole} 值
     * @param loginIdentifier 调用方提供的 {@code loginIdentifier} 值
     * @param credentials 调用方提供的 {@code credentials} 值
     */
    public Cc4cAuthenticationToken(AccountRole requestedRole, String loginIdentifier, String credentials) {
        super(null);
        this.requestedRole = requestedRole;
        this.loginIdentifier = loginIdentifier;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    /**
     * 创建 Cc4cAuthenticationToken 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param principal 调用方提供的 {@code principal} 值
     * @param authorities 调用方提供的 {@code authorities} 值
     */
    Cc4cAuthenticationToken(Cc4cPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.requestedRole = principal.role();
        this.loginIdentifier = principal.actorId();
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 AccountRole 结果
     */
    AccountRole requestedRole() {
        return requestedRole;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    String loginIdentifier() {
        return loginIdentifier;
    }

    /**
     * 读取认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 Object 结果
     */
    @Override
    public Object getCredentials() {
        return credentials;
    }

    /**
     * 读取认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 Object 结果
     */
    @Override
    public Object getPrincipal() {
        return principal == null ? loginIdentifier : principal;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }
}
