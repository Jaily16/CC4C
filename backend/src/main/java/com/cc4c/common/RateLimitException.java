package com.cc4c.common;

/** 携带限流范围及至少一秒的重试等待，供安全处理和 HTTP 429 响应使用。 */
public final class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String scope;

    /**
     * 保存限流范围，并将 Retry-After 等待约束为至少一秒。
     *
     * @param retryAfterSeconds 建议等待的秒数；小于一时保存为一
     */
    public RateLimitException(long retryAfterSeconds) {
        this(retryAfterSeconds, "unknown");
    }

    /**
     * 保存限流范围，并将 Retry-After 等待约束为至少一秒。
     *
     * @param retryAfterSeconds 建议等待的秒数；小于一时保存为一
     * @param scope 触发限流的账户、IP 或操作范围标识
     */
    public RateLimitException(long retryAfterSeconds, String scope) {
        super("请求过于频繁，请稍后重试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.scope = scope;
    }

    /**
     * 返回限流响应建议等待的秒数。
     *
     * @return 至少为一的等待秒数
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * 返回触发限流的范围标识。
     *
     * @return 保存的限流范围；单参数构造器使用 unknown
     */
    public String scope() {
        return scope;
    }
}
