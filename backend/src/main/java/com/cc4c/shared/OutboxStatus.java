package com.cc4c.shared;

/** OutboxStatus 枚举稳定的状态或协议取值，避免调用方自行解释字符串。 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    DELIVERED,
    PUBLISH_FAILED,
    DEAD,
    EXPIRED,
    IGNORED;

    public boolean recoverable() {
        return this == PUBLISH_FAILED || this == DEAD;
    }
}
