package com.cc4c.identity.api;

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
 * Cc4cPrincipal 定义模块之间稳定、可验证的公开契约。
 *
 * @param role 调用方提供的 {@code role} 值
 * @param actorId 目标对象的稳定标识
 * @param displayName 调用方提供的 {@code displayName} 值
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Cc4cPrincipal(AccountRole role, String actorId, String displayName) implements UserDetails, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查询并返回 Cc4cPrincipal 中与 getName 对应的数据，不改变业务状态。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @JsonIgnore
    public String getName() {
        return role.name() + ":" + actorId;
    }

    /**
     * 查询并返回 Cc4cPrincipal 中与 getAuthorities 对应的数据，不改变业务状态。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * 查询并返回 Cc4cPrincipal 中与 getPassword 对应的数据，不改变业务状态。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Override
    @JsonIgnore
    public String getPassword() {
        return "";
    }

    /**
     * 查询并返回 Cc4cPrincipal 中与 getUsername 对应的数据，不改变业务状态。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Override
    @JsonIgnore
    public String getUsername() {
        return getName();
    }

    /**
     * 判断 Cc4cPrincipal 中与 isAccountNonExpired 对应的条件是否成立。
     *
     * @return 当前条件是否成立
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断 Cc4cPrincipal 中与 isAccountNonLocked 对应的条件是否成立。
     *
     * @return 当前条件是否成立
     */
    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 判断 Cc4cPrincipal 中与 isCredentialsNonExpired 对应的条件是否成立。
     *
     * @return 当前条件是否成立
     */
    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 判断 Cc4cPrincipal 中与 isEnabled 对应的条件是否成立。
     *
     * @return 当前条件是否成立
     */
    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }
}
