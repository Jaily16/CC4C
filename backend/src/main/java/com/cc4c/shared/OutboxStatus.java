package com.cc4c.shared;

/**
 * OutboxStatus 枚举共享基础设施的有限状态或协议取值。
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    DELIVERED,
    PUBLISH_FAILED,
    DEAD,
    EXPIRED,
    IGNORED;

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    public boolean recoverable() {
        return this == PUBLISH_FAILED || this == DEAD;
    }
}
