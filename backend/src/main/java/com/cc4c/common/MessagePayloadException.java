package com.cc4c.common;

/**
 * 表示共享基础设施处理中可分类且可安全映射的失败。
 */
public final class MessagePayloadException extends RuntimeException {
    private final String errorCode;

    /**
     * 创建 MessagePayloadException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @param message 当前处理的消息或用户提示
     */
    public MessagePayloadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建 MessagePayloadException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @param message 当前处理的消息或用户提示
     * @param cause 调用方提供的 {@code cause} 值
     */
    public MessagePayloadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    public String errorCode() {
        return errorCode;
    }
}
