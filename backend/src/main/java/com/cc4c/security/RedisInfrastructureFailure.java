package com.cc4c.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/**
 * RedisInfrastructureFailure 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
public final class RedisInfrastructureFailure {
    private static final int MAX_CAUSE_DEPTH = 32;
    private static final String LETTUCE_PACKAGE_PREFIX = "io.lettuce.core.";

    /**
     * 创建 RedisInfrastructureFailure 实例，不触发外部 I/O。
     */
    private RedisInfrastructureFailure() {}

    /**
     * 判断当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param failure 调用方提供的 {@code failure} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isUnavailable(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH && visited.add(current)) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof RedisSystemException
                    || current.getClass().getName().startsWith(LETTUCE_PACKAGE_PREFIX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
