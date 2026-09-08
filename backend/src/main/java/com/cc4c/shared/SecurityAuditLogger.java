package com.cc4c.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SecurityAuditLogger 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final SecurityKeyHasher hasher;

    /**
     * 创建 SecurityAuditLogger 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param hasher 调用方提供的 {@code hasher} 值
     */
    public SecurityAuditLogger(SecurityKeyHasher hasher) {
        this.hasher = hasher;
    }

    /**
     * 执行 SecurityAuditLogger 中的 authentication 职责，并保持既有权限、事务与副作用边界。
     *
     * @param role 调用方提供的 {@code role} 值
     * @param identifier 调用方提供的 {@code identifier} 值
     * @param result 调用方提供的 {@code result} 值
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
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
     * 执行 SecurityAuditLogger 中的 action 职责，并保持既有权限、事务与副作用边界。
     *
     * @param action 调用方提供的 {@code action} 值
     * @param role 调用方提供的 {@code role} 值
     * @param actorId 目标对象的稳定标识
     * @param result 调用方提供的 {@code result} 值
     * @param remoteAddress 调用方提供的 {@code remoteAddress} 值
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
