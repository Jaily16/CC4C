package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

class RedisInfrastructureFailureTest {

    @Test
    void recognizesRedisFailuresThroughFrameworkWrappers() {
        RuntimeException failure = new QueryTimeoutException(
                "outer timeout must not be logged",
                new RedisSystemException("redis endpoint must not leak", new IllegalStateException("test cause")));

        assertTrue(RedisInfrastructureFailure.isUnavailable(failure));
    }

    @Test
    void doesNotMisclassifyUnrelatedDatabaseTimeouts() {
        assertFalse(RedisInfrastructureFailure.isUnavailable(new QueryTimeoutException(
                "database query timed out", new IllegalStateException("database unavailable"))));
    }
}
