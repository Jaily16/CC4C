package com.cc4c.support.messaging;

/** 描述事件从待发布、发布中到送达、失败、过期或人工忽略的持久化状态。 */
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
     * 判断状态是否允许进入人工恢复流程；过期及收件人条件由操作服务继续校验。
     *
     * @return 仅 PUBLISH_FAILED 或 DEAD 为 true
     */
    public boolean recoverable() {
        return this == PUBLISH_FAILED || this == DEAD;
    }
}
