package com.cc4c.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 记录认证及身份操作审计；登录标识和来源地址使用 HMAC 摘要，操作审计保留业务 actor ID。 */
@Component
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final SecurityKeyHasher hasher;

    /**
     * 接入安全标识摘要服务，避免将来源地址与登录标识原文写入日志。
     *
     * @param hasher 带秘密 pepper 的 HMAC 摘要服务
     */
    public SecurityAuditLogger(SecurityKeyHasher hasher) {
        this.hasher = hasher;
    }

    /**
     * 记录角色、认证结果及登录标识和远端地址的摘要。
     *
     * @param role 审计所属身份角色
     * @param identifier 待摘要化的登录标识
     * @param result 审计结果分类
     * @param remoteAddress 待摘要化的请求远端地址
     */
    public void authentication(String role, String identifier, String result, String remoteAddress) {
        log.atInfo()
                .addKeyValue("event", "security_audit")
                .addKeyValue("action", "authentication")
                .addKeyValue("role", role)
                .addKeyValue("subject", hasher.hash(identifier))
                .addKeyValue("result", result)
                .addKeyValue("remote", hasher.hash(remoteAddress))
                .log("Security audit event");
    }

    /**
     * 记录动作、角色、业务身份 ID、结果及远端地址摘要；actor ID 不做哈希转换。
     *
     * @param action 身份操作名称
     * @param role 审计所属身份角色
     * @param actorId 写入审计的业务身份 ID
     * @param result 审计结果分类
     * @param remoteAddress 待摘要化的请求远端地址
     */
    public void action(String action, String role, String actorId, String result, String remoteAddress) {
        log.atInfo()
                .addKeyValue("event", "security_audit")
                .addKeyValue("action", action)
                .addKeyValue("role", role)
                .addKeyValue("actor", actorId)
                .addKeyValue("result", result)
                .addKeyValue("remote", hasher.hash(remoteAddress))
                .log("Security audit event");
    }
}
