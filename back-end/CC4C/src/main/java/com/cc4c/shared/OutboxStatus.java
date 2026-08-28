package com.cc4c.shared;

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
