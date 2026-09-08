package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SecurityCurrentActor 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
final class SecurityCurrentActor implements CurrentActor {
    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 存在时返回目标值，否则返回空的 Optional
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
     * 校验认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 按当前规则计算或读取的数值
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
     * 校验认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    @Override
    public String requiredAdministratorId() {
        return required(AccountRole.ADMIN).id();
    }

    /**
     * 校验认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param role 当前身份的固定角色
     * @return 当前操作产生的 ActorIdentity 结果
     */
    private ActorIdentity required(AccountRole role) {
        ActorIdentity actor = current().orElseThrow(this::unauthorized);
        if (actor.role() != role) {
            throw forbidden();
        }
        return actor;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 BusinessException 结果
     */
    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, BusinessCode.UNAUTHORIZED, "请先登录");
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @return 当前操作产生的 BusinessException 结果
     */
    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权执行此操作");
    }
}
