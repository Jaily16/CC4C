package com.cc4c.common;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** 校验 HTTP、AMQP 与 MDC 使用的关联 ID，并在嵌套调用结束时恢复线程原有关联值。 */
public final class CorrelationIds {
    public static final String HEADER = "X-Request-ID";
    public static final String AMQP_HEADER = "X-CC4C-Correlation-Id";
    public static final String MDC_KEY = "request_id";
    public static final String REQUEST_ATTRIBUTE = CorrelationIds.class.getName() + ".requestId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    /** 禁止实例化关联 ID 工具类。 */
    private CorrelationIds() {}

    /**
     * 保留符合白名单的关联 ID，否则生成 UUID 字符串。
     *
     * @param candidate 待校验的关联 ID，允许 null
     * @return 由 16–64 位字母、数字、下划线或连字符组成的关联 ID
     */
    public static String normalizeOrGenerate(String candidate) {
        return normalize(candidate, UUID.randomUUID().toString());
    }

    /**
     * 依次选择合法候选值、合法回退值或新 UUID，不截断非法输入。
     *
     * @param candidate 待校验的关联 ID，允许 null
     * @param fallback 候选值无效时使用的关联 ID；无效或为空时生成 UUID
     * @return 合法候选值、合法回退值或新 UUID
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
     * 优先使用当前线程 MDC 中的合法关联 ID，否则尝试回退值或生成 UUID。
     *
     * @param fallback 候选值无效时使用的关联 ID；无效或为空时生成 UUID
     * @return 可用于本次请求或消息关联的 ID
     */
    public static String currentOr(String fallback) {
        return normalize(MDC.get(MDC_KEY), fallback);
    }

    /**
     * 设置本次 MDC 关联 ID，并返回用于恢复进入前状态的作用域。
     *
     * @param correlationId 本范围使用的关联 ID；非法或为空时生成 UUID
     * @return 关闭时恢复原 MDC 值的作用域
     */
    public static Scope open(String correlationId) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, normalizeOrGenerate(correlationId));
        return new Scope(previous);
    }

    /** 保存进入关联范围前的 MDC 值，关闭时恢复该值或移除本轮新增项。 */
    public static final class Scope implements AutoCloseable {
        private final String previous;

        /**
         * 保存进入当前关联范围前的 MDC 值。
         *
         * @param previous 进入范围前的 MDC 值，null 表示原来没有该项
         */
        private Scope(String previous) {
            this.previous = previous;
        }

        /** 恢复进入前的关联 ID；此前没有关联值时移除 MDC 项。 */
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
