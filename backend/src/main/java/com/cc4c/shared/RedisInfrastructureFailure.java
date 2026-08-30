package com.cc4c.shared;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

/** RedisInfrastructureFailure 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class RedisInfrastructureFailure {
    private static final int MAX_CAUSE_DEPTH = 32;
    private static final String LETTUCE_PACKAGE_PREFIX = "io.lettuce.core.";

    private RedisInfrastructureFailure() {}

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
