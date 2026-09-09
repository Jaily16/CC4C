package com.cc4c.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/** 沿有限深度的异常原因链识别 Redis 连接、系统或 Lettuce 故障。 */
public final class RedisInfrastructureFailure {
    private static final int MAX_CAUSE_DEPTH = 32;
    private static final String LETTUCE_PACKAGE_PREFIX = "io.lettuce.core.";

    /** 禁止实例化异常分类工具。 */
    private RedisInfrastructureFailure() {}

    /**
     * 最多检查 32 层原因，并按对象身份检测循环；命中 Spring Redis 或 Lettuce 异常即返回 true。
     *
     * @param failure 待检查的异常，可以为空
     * @return 原因链中存在已知 Redis 基础设施异常时为 true
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
