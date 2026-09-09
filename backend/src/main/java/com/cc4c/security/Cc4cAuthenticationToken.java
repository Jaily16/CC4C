package com.cc4c.security;

import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** 承载业务登录请求的角色、标识和临时凭据；凭据可在认证后显式擦除。 */
public final class Cc4cAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AccountRole requestedRole;
    private final String loginIdentifier;
    private Object credentials;
    private Cc4cPrincipal principal;

    /**
     * 创建未认证登录请求，保存角色、登录标识和待验证密码。
     *
     * @param requestedRole 本次请求的 USER 或 ADMIN 角色
     * @param loginIdentifier 用户邮箱或管理员编号
     * @param credentials 临时明文登录密码，不得记录
     */
    public Cc4cAuthenticationToken(AccountRole requestedRole, String loginIdentifier, String credentials) {
        super(null);
        this.requestedRole = requestedRole;
        this.loginIdentifier = loginIdentifier;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    /**
     * 由已有身份和权限创建已认证 Token，不设置登录凭据。
     *
     * @param principal 已认证的业务身份快照
     * @param authorities 认证身份拥有的权限集合
     */
    Cc4cAuthenticationToken(Cc4cPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.requestedRole = principal.role();
        this.loginIdentifier = principal.actorId();
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * 读取本次登录请求指定的业务角色。
     *
     * @return USER 或 ADMIN
     */
    AccountRole requestedRole() {
        return requestedRole;
    }

    /**
     * 读取登录标识；请求构造时为邮箱或管理员编号，身份构造时为身份 ID。
     *
     * @return 登录查询标识
     */
    String loginIdentifier() {
        return loginIdentifier;
    }

    /**
     * 读取尚未擦除的登录凭据。
     *
     * @return 凭据对象；未设置或已擦除时为空
     */
    @Override
    public Object getCredentials() {
        return credentials;
    }

    /**
     * 已设置身份时返回身份对象，否则返回登录标识。
     *
     * @return 身份对象或登录标识字符串
     */
    @Override
    public Object getPrincipal() {
        return principal == null ? loginIdentifier : principal;
    }

    /** 先执行父类凭据清理，再将本 Token 的凭据引用置空。 */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }
}
