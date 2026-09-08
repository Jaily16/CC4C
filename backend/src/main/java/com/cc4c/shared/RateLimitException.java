package com.cc4c.shared;

/**
 * 表示共享基础设施处理中可分类且可安全映射的失败。
 */
public final class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String scope;

    /**
     * 创建 RateLimitException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param retryAfterSeconds 调用方提供的 {@code retryAfterSeconds} 值
     */
    public RateLimitException(long retryAfterSeconds) {
        this(retryAfterSeconds, "unknown");
    }

    /**
     * 创建 RateLimitException 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param retryAfterSeconds 调用方提供的 {@code retryAfterSeconds} 值
     * @param scope 调用方提供的 {@code scope} 值
     */
    public RateLimitException(long retryAfterSeconds, String scope) {
        super("请求过于频繁，请稍后重试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.scope = scope;
    }

    /**
     * 重试当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前规则计算或读取的数值
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    public String scope() {
        return scope;
    }
}
