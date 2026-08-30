package com.cc4c.shared;

/** RateLimitException 表示可被统一错误处理器识别的业务故障。 */
public final class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String scope;

    public RateLimitException(long retryAfterSeconds) {
        this(retryAfterSeconds, "unknown");
    }

    public RateLimitException(long retryAfterSeconds, String scope) {
        super("请求过于频繁，请稍后重试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.scope = scope;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String scope() {
        return scope;
    }
}
