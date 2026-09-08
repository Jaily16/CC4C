package com.cc4c.common;

/**
 * 表示共享基础设施处理中可分类且可安全映射的失败。
 */
public final class MailDeliveryException extends RuntimeException {
    private final String errorCode;
    private final boolean permanent;

    /**
     * 创建 MailDeliveryException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param errorCode 调用方提供的 {@code errorCode} 值
     * @param permanent 调用方提供的 {@code permanent} 值
     * @param cause 调用方提供的 {@code cause} 值
     */
    public MailDeliveryException(String errorCode, boolean permanent, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.permanent = permanent;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    public boolean permanent() {
        return permanent;
    }
}
