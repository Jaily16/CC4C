package com.cc4c.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final SecurityKeyHasher hasher;

    public SecurityAuditLogger(SecurityKeyHasher hasher) {
        this.hasher = hasher;
    }

    public void authentication(String role, String identifier, String result, String remoteAddress) {
        log.info(
                "security_action=authentication role={} subject={} result={} remote={}",
                role,
                hasher.hash(identifier),
                result,
                hasher.hash(remoteAddress));
    }

    public void action(String action, String role, String actorId, String result, String remoteAddress) {
        log.info(
                "security_action={} role={} actor={} result={} remote={}",
                action,
                role,
                actorId,
                result,
                hasher.hash(remoteAddress));
    }
}
