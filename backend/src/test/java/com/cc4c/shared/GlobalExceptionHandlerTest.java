package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;

class GlobalExceptionHandlerTest {

    @Test
    void unexpectedExceptionsReturnGeneric500WithoutExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleUnexpectedException(
                new IllegalStateException("database password and request body must not leak"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(BusinessCode.INTERNAL_ERROR.code(), response.getBody().code());
        assertEquals("Request processing failed", response.getBody().msg());
        assertFalse(response.getBody().msg().contains("password"));
    }

    @Test
    void persistenceConflictsMapTo409And422WithoutSqlDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var duplicate = handler.handleDuplicateKey(new DuplicateKeyException("secret SQL"));
        var invalidReference = handler.handleDataIntegrity(new DataIntegrityViolationException("secret SQL"));

        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals(BusinessCode.CONFLICT.code(), duplicate.getBody().code());
        assertFalse(duplicate.getBody().msg().contains("SQL"));
        assertEquals(422, invalidReference.getStatusCode().value());
        assertEquals(
                BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION.code(),
                invalidReference.getBody().code());
        assertFalse(invalidReference.getBody().msg().contains("SQL"));
    }

    @Test
    void redisCommandFailuresMapToGeneric503WithoutInfrastructureDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleRedisUnavailable(new RedisSystemException(
                "redis-host-and-credential-must-not-leak", new IllegalStateException("test cause")));

        assertEquals(503, response.getStatusCode().value());
        assertEquals(BusinessCode.SERVICE_UNAVAILABLE.code(), response.getBody().code());
        assertFalse(response.getBody().msg().contains("redis-host"));
    }

    @Test
    void wrappedRedisFailuresMapTo503WhileDatabaseTimeoutsRemainGeneric500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var redis = handler.handleUnexpectedException(new QueryTimeoutException(
                "outer timeout must not leak",
                new RedisSystemException("redis-host-must-not-leak", new IllegalStateException("test cause"))));
        var database = handler.handleUnexpectedException(new QueryTimeoutException(
                "database details must not leak", new IllegalStateException("database unavailable")));

        assertEquals(503, redis.getStatusCode().value());
        assertEquals(BusinessCode.SERVICE_UNAVAILABLE.code(), redis.getBody().code());
        assertFalse(redis.getBody().msg().contains("redis-host"));
        assertEquals(500, database.getStatusCode().value());
        assertEquals(BusinessCode.INTERNAL_ERROR.code(), database.getBody().code());
    }
}
