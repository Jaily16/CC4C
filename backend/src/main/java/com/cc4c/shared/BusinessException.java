package com.cc4c.shared;

import org.springframework.http.HttpStatus;

/** BusinessException 表示可被统一错误处理器识别的业务故障。 */
public final class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final int code;
    private final Object data;

    public BusinessException(HttpStatus status, BusinessCode code, String message) {
        this(status, code.code(), false, message);
    }

    public BusinessException(HttpStatus status, int code, Object data, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public HttpStatus status() {
        return status;
    }

    public int code() {
        return code;
    }

    public Object data() {
        return data;
    }
}
