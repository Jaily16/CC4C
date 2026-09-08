package com.cc4c.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Cc4cSessionAuthenticationToken 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
final class Cc4cSessionAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Cc4cPrincipal principal;

    /**
     * 创建 Cc4cSessionAuthenticationToken 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param principal 调用方提供的 {@code principal} 值
     * @param authorities 调用方提供的 {@code authorities} 值
     * @param details 调用方提供的 {@code details} 值
     */
    @JsonCreator
    Cc4cSessionAuthenticationToken(
            @JsonProperty("principal") Cc4cPrincipal principal,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("details") Object details) {
        super(authorities);
        this.principal = principal;
        setDetails(details);
        setAuthenticated(true);
    }

    /**
     * 创建 Cc4cSessionAuthenticationToken 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param principal 调用方提供的 {@code principal} 值
     * @param authorities 调用方提供的 {@code authorities} 值
     */
    Cc4cSessionAuthenticationToken(Cc4cPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        this(principal, authorities, null);
    }

    /**
     * 读取认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 Object 结果
     */
    @Override
    @JsonIgnore
    public Object getCredentials() {
        return null;
    }

    /**
     * 读取认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 Cc4cPrincipal 结果
     */
    @Override
    public Cc4cPrincipal getPrincipal() {
        return principal;
    }
}
