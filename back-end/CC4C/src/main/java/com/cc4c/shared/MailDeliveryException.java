package com.cc4c.shared;

public final class MailDeliveryException extends RuntimeException {
    private final String errorCode;
    private final boolean permanent;

    public MailDeliveryException(String errorCode, boolean permanent, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.permanent = permanent;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean permanent() {
        return permanent;
    }
}
