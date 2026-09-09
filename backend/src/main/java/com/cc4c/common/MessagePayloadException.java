package com.cc4c.common;

/** 区分消息载荷校验或编解码失败，向上层提供受控错误码并保留内部原因。 */
public final class MessagePayloadException extends RuntimeException {
    private final String errorCode;

    /**
     * 保存载荷错误码、内部诊断消息及可选的底层原因。
     *
     * @param errorCode 不含邮件内容、密文或连接凭据的受控错误码
     * @param message 由当前异常保存的提示或内部诊断消息
     */
    public MessagePayloadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 保存载荷错误码、内部诊断消息及可选的底层原因。
     *
     * @param errorCode 不含邮件内容、密文或连接凭据的受控错误码
     * @param message 由当前异常保存的提示或内部诊断消息
     * @param cause 供内部诊断保留的底层原因，不作为管理摘要直接输出
     */
    public MessagePayloadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 返回用于消息失败分类的受控载荷错误码。
     *
     * @return 受控载荷错误码
     */
    public String errorCode() {
        return errorCode;
    }
}
