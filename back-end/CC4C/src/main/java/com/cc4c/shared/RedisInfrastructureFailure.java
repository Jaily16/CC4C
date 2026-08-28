package com.cc4c.shared;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class RedisInfrastructureFailure {
    private static final int MAX_CAUSE_DEPTH = 32;
    private static final String LETTUCE_PACKAGE_PREFIX = "io.lettuce.core.";

    private RedisInfrastructureFailure() {
    }

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
