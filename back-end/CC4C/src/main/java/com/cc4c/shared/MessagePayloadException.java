package com.cc4c.shared;

public final class MessagePayloadException extends RuntimeException {
    private final String errorCode;

    public MessagePayloadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MessagePayloadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
