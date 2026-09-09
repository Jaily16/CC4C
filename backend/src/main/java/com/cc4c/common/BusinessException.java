package com.cc4c.common;

import org.springframework.http.HttpStatus;

/** 携带可公开的 HTTP 状态、业务码、响应数据和提示，由全局异常处理器转换为统一响应。 */
public final class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final int code;
    private final Object data;

    /**
     * 保存将由全局处理器公开返回的状态、业务码、数据及提示。
     *
     * @param status 对外响应使用的 HTTP 状态
     * @param code 统一响应体中的业务码
     * @param message 由当前异常保存的提示或内部诊断消息
     */
    public BusinessException(HttpStatus status, BusinessCode code, String message) {
        this(status, code.code(), false, message);
    }

    /**
     * 保存将由全局处理器公开返回的状态、业务码、数据及提示。
     *
     * @param status 对外响应使用的 HTTP 状态
     * @param code 统一响应体中的业务码
     * @param data 对外响应的数据字段；应由调用方保证可公开
     * @param message 由当前异常保存的提示或内部诊断消息
     */
    public BusinessException(HttpStatus status, int code, Object data, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    /**
     * 返回该业务异常应使用的 HTTP 状态。
     *
     * @return 保存的 HTTP 状态
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 返回统一响应体中的业务码。
     *
     * @return 保存的业务码
     */
    public int code() {
        return code;
    }

    /**
     * 返回统一响应体中的数据字段。
     *
     * @return 保存的响应数据
     */
    public Object data() {
        return data;
    }
}
