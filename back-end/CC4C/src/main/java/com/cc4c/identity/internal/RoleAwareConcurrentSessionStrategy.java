package com.cc4c.identity.internal;

import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;

final class RoleAwareConcurrentSessionStrategy
        extends ConcurrentSessionControlAuthenticationStrategy {

    RoleAwareConcurrentSessionStrategy(SessionRegistry sessionRegistry) {
        super(sessionRegistry);
        setExceptionIfMaximumExceeded(false);
    }

    @Override
    protected int getMaximumSessionsForThisUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Cc4cPrincipal principal
                && principal.role() == AccountRole.ADMIN) {
            return 1;
        }
        return 3;
    }
}
