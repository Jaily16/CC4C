package com.cc4c.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;

/** 按角色限制并发会话；超限时使旧会话过期，不阻止本次新登录。 */
public final class RoleAwareConcurrentSessionStrategy extends ConcurrentSessionControlAuthenticationStrategy {

    /**
     * 接入会话注册表，并关闭超限时直接拒绝认证的策略。
     *
     * @param sessionRegistry 持久化会话索引注册表
     */
    public RoleAwareConcurrentSessionStrategy(SessionRegistry sessionRegistry) {
        super(sessionRegistry);
        setExceptionIfMaximumExceeded(false);
    }

    /**
     * 管理员最多一个并发会话，其他身份使用三个会话上限。
     *
     * @param authentication 待处理的认证对象
     * @return 管理员为 1，其他为 3
     */
    @Override
    protected int getMaximumSessionsForThisUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Cc4cPrincipal principal && principal.role() == AccountRole.ADMIN) {
            return 1;
        }
        return 3;
    }
}
