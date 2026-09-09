package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 将 Spring Security 上下文转换为业务身份，拒绝把其他认证体系的 Principal 当作用户。 */
@Component
final class SecurityCurrentActor implements CurrentActor {
    /**
     * 仅接受已认证且 Principal 为 Cc4cPrincipal 的上下文，并投影业务身份字段。
     *
     * @return 可识别的业务身份，否则为空 Optional
     */
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

    /**
     * 严格要求 USER；身份 ID 不能解析为 long 时按无权限处理。
     *
     * @return 当前用户 ID
     */
    @Override
    public long requiredUserId() {
        ActorIdentity actor = required(AccountRole.USER);
        try {
            return Long.parseLong(actor.id());
        } catch (NumberFormatException exception) {
            throw forbidden();
        }
    }

    /**
     * 严格要求 ADMIN 并返回管理员编号，不进行数字转换。
     *
     * @return 当前管理员 ID
     */
    @Override
    public String requiredAdministratorId() {
        return required(AccountRole.ADMIN).id();
    }

    /**
     * 无业务身份时抛出 401，角色不匹配时抛出 403。
     *
     * @param role 业务角色 USER 或 ADMIN
     * @return 满足指定角色的业务身份
     */
    private ActorIdentity required(AccountRole role) {
        ActorIdentity actor = current().orElseThrow(this::unauthorized);
        if (actor.role() != role) {
            throw forbidden();
        }
        return actor;
    }

    /**
     * 构造要求重新登录的 401 业务异常。
     *
     * @return 未认证异常
     */
    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
    }

    /**
     * 构造拒绝当前操作的 403 业务异常。
     *
     * @return 权限不足异常
     */
    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权执行此操作");
    }
}
