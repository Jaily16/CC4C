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

/** 持久化已认证业务身份、权限和 details；JSON 恢复使用显式构造器，永不保存登录密码。 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
final class Cc4cSessionAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Cc4cPrincipal principal;

    /**
     * 恢复 Principal、权限及 details，并将会话 Token 标记为已认证。
     *
     * @param principal 已认证的业务身份快照
     * @param authorities 认证身份拥有的权限集合
     * @param details 可空的认证附加信息，不应包含密码
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
     * 使用空 details 创建已认证会话 Token。
     *
     * @param principal 已认证的业务身份快照
     * @param authorities 认证身份拥有的权限集合
     */
    Cc4cSessionAuthenticationToken(Cc4cPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        this(principal, authorities, null);
    }

    /**
     * 会话 Token 不保存登录凭据。
     *
     * @return 始终为空
     */
    @Override
    @JsonIgnore
    public Object getCredentials() {
        return null;
    }

    /**
     * 返回会话中保存的业务身份快照。
     *
     * @return 业务 Principal
     */
    @Override
    public Cc4cPrincipal getPrincipal() {
        return principal;
    }
}
