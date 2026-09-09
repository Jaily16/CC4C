package com.cc4c.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 可存入 Session 的业务身份，不保存密码；身份名以角色前缀隔离用户和管理员索引。
 *
 * @param role 业务角色 USER 或 ADMIN
 * @param actorId 角色所属身份 ID
 * @param displayName 身份展示名
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Cc4cPrincipal(AccountRole role, String actorId, String displayName) implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 将角色和身份 ID 组合为会话索引名。
     *
     * @return USER:ID 或 ADMIN:ID
     */
    @JsonIgnore
    public String getName() {
        return role.name() + ":" + actorId;
    }

    /**
     * 由业务角色生成唯一的 Spring Security 角色权限。
     *
     * @return 仅含 ROLE_USER 或 ROLE_ADMIN 的权限列表
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * 此身份快照不保存密码，满足 UserDetails 接口时返回空字符串。
     *
     * @return 空字符串
     */
    @Override
    @JsonIgnore
    public String getPassword() {
        return "";
    }

    /**
     * 返回角色隔离的身份名，供 UserDetails 和会话索引使用。
     *
     * @return 与 getName 相同的身份名
     */
    @Override
    @JsonIgnore
    public String getUsername() {
        return getName();
    }

    /**
     * 固定报告账户未过期，不执行实时账户查询。
     *
     * @return 始终为 true
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 固定报告账户未锁定，不查询登录限流状态。
     *
     * @return 始终为 true
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 固定报告凭据未过期，此快照不持有密码或凭据期限。
     *
     * @return 始终为 true
     */
    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 固定报告此已建立身份可用，不重新读取数据库账户状态。
     *
     * @return 始终为 true
     */
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}
