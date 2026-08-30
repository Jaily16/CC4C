package com.cc4c.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
/** SecurityAuditLogger 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final SecurityKeyHasher hasher;

    public SecurityAuditLogger(SecurityKeyHasher hasher) {
        this.hasher = hasher;
    }

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
