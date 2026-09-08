package com.cc4c.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;

/**
 * RoleAwareConcurrentSessionStrategy 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
public final class RoleAwareConcurrentSessionStrategy extends ConcurrentSessionControlAuthenticationStrategy {

    /**
     * 创建 RoleAwareConcurrentSessionStrategy 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param sessionRegistry 调用方提供的 {@code sessionRegistry} 值
     */
    public RoleAwareConcurrentSessionStrategy(SessionRegistry sessionRegistry) {
        super(sessionRegistry);
        setExceptionIfMaximumExceeded(false);
    }

    /**
     * 读取认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param authentication 调用方提供的 {@code authentication} 值
     * @return 按当前规则计算或读取的数值
     */
    @Override
    protected int getMaximumSessionsForThisUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Cc4cPrincipal principal && principal.role() == AccountRole.ADMIN) {
            return 1;
        }
        return 3;
    }
}
