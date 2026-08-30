package com.cc4c.identity.internal;

import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.ActorIdentity;
import com.cc4c.identity.api.Cc4cPrincipal;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
final class SecurityCurrentActor implements CurrentActor {
    @Override
    public Optional<ActorIdentity> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Cc4cPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(new ActorIdentity(principal.role(), principal.actorId(), principal.displayName()));
    }

    @Override
    public long requiredUserId() {
        ActorIdentity actor = required(AccountRole.USER);
        try {
            return Long.parseLong(actor.id());
        } catch (NumberFormatException exception) {
            throw forbidden();
        }
    }

    @Override
    public String requiredAdministratorId() {
        return required(AccountRole.ADMIN).id();
    }

    private ActorIdentity required(AccountRole role) {
        ActorIdentity actor = current().orElseThrow(this::unauthorized);
        if (actor.role() != role) {
            throw forbidden();
        }
        return actor;
    }

    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权执行此操作");
    }
}
