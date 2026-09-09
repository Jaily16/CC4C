package com.cc4c.support.messaging;

/**
 * 记录 broker 发布是否确认及失败分类，不代表消费业务已完成。
 *
 * @param accepted broker 是否已确认且未退回
 * @param errorCode 不含敏感正文的失败分类码
 */
record PublishOutcome(boolean accepted, String errorCode) {
    /**
     * 创建 broker 已确认接收的发布结果。
     *
     * @return accepted 为 true 且错误码为空
     */
    static PublishOutcome confirmed() {
        return new PublishOutcome(true, null);
    }

    /**
     * 创建带分类码的发布失败结果。
     *
     * @param errorCode 不含敏感正文的失败分类码
     * @return accepted 为 false 的结果
     */
    static PublishOutcome failed(String errorCode) {
        return new PublishOutcome(false, errorCode);
    }
}
