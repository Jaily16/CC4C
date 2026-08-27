package com.cc4c.shared;

public final class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("请求过于频繁，请稍后重试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
