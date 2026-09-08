package com.cc4c.identity.internal;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * 协调身份认证用例及其持久化、安全和外部协作边界。
 */
@Service
final class SessionRevocationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    /**
     * 创建 SessionRevocationService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param sessions 调用方提供的 {@code sessions} 值
     */
    SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param principalName 调用方提供的 {@code principalName} 值
     */
    void revokePrincipal(String principalName) {
        sessions.findByPrincipalName(principalName).keySet().forEach(sessions::deleteById);
    }
}
