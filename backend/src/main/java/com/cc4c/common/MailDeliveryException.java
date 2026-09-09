package com.cc4c.common;

/** 携带邮件失败的受控错误码和永久失败标记，供可靠消息处理器决定重试或终止。 */
public final class MailDeliveryException extends RuntimeException {
    private final String errorCode;
    private final boolean permanent;

    /**
     * 保存邮件失败码、永久失败标记及底层原因。
     *
     * @param errorCode 不含邮件内容、密文或连接凭据的受控错误码
     * @param permanent true 表示永久邮件错误，不进入临时失败重试
     * @param cause 供内部诊断保留的底层原因，不作为管理摘要直接输出
     */
    public MailDeliveryException(String errorCode, boolean permanent, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.permanent = permanent;
    }

    /**
     * 返回可用于消息状态摘要的受控邮件错误码。
     *
     * @return 受控邮件错误码
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * 返回该邮件错误是否应直接终止消费重试。
     *
     * @return 永久失败时为 true，可重试失败时为 false
     */
    public boolean permanent() {
        return permanent;
    }
}
