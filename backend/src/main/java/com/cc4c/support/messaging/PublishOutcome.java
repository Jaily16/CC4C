package com.cc4c.support.messaging;

/**
 * PublishOutcome 以不可变结构承载共享基础设施数据，并保持现有字段语义。
 *
 * @param accepted 调用方提供的 {@code accepted} 值
 * @param errorCode 调用方提供的 {@code errorCode} 值
 */
record PublishOutcome(boolean accepted, String errorCode) {
    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 当前操作产生的 PublishOutcome 结果
     */
    static PublishOutcome confirmed() {
        return new PublishOutcome(true, null);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @return 当前操作产生的 PublishOutcome 结果
     */
    static PublishOutcome failed(String errorCode) {
        return new PublishOutcome(false, errorCode);
    }
}
