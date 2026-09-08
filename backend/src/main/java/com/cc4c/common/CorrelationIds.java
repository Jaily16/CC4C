package com.cc4c.common;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * CorrelationIds 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
public final class CorrelationIds {
    public static final String HEADER = "X-Request-ID";
    public static final String AMQP_HEADER = "X-CC4C-Correlation-Id";
    public static final String MDC_KEY = "request_id";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".requestId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    /**
     * 创建 CorrelationIds 实例，不触发外部 I/O。
     */
    private CorrelationIds() {}

    /**
     * 规范化当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param candidate 调用方提供的 {@code candidate} 值
     * @return 按当前协议生成或读取的字符串值
     */
    public static String normalizeOrGenerate(String candidate) {
        return normalize(candidate, UUID.randomUUID().toString());
    }

    /**
     * 规范化当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param candidate 调用方提供的 {@code candidate} 值
     * @param fallback 调用方提供的 {@code fallback} 值
     * @return 按当前协议生成或读取的字符串值
     */
    public static String normalize(String candidate, String fallback) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        if (fallback != null && SAFE.matcher(fallback).matches()) {
            return fallback;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param fallback 调用方提供的 {@code fallback} 值
     * @return 按当前协议生成或读取的字符串值
     */
    public static String currentOr(String fallback) {
        return normalize(MDC.get(MDC_KEY), fallback);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param correlationId 目标对象的稳定标识
     * @return 当前操作产生的 Scope 结果
     */
    public static Scope open(String correlationId) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, normalizeOrGenerate(correlationId));
        return new Scope(previous);
    }

    /**
     * Scope 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
     */
    public static final class Scope implements AutoCloseable {
        private final String previous;

        /**
         * 创建 Scope 并保存所需协作组件；构造阶段不主动执行外部业务操作。
         *
         * @param previous 调用方提供的 {@code previous} 值
         */
        private Scope(String previous) {
            this.previous = previous;
        }

        /**
         * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
         */
        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
