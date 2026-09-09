package com.cc4c.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** 按角色隔离的 Principal 名查询并删除该身份的全部持久化会话。 */
@Service
public final class SessionRevocationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    /**
     * 接入支持 Principal 索引查询的 Spring Session 仓库。
     *
     * @param sessions 按 Principal 名建立索引的会话仓库
     */
    SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * 删除当前索引中该身份的所有 Session；仓库异常直接传播，不只撤销当前请求会话。
     *
     * @param principalName 包含 USER: 或 ADMIN: 前缀的完整身份名
     */
    public void revokePrincipal(String principalName) {
        sessions.findByPrincipalName(principalName).keySet().forEach(sessions::deleteById);
    }
}
