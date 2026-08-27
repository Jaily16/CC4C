package com.cc4c.shared;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        var invalidReference = handler.handleDataIntegrity(
                new DataIntegrityViolationException("secret SQL"));

        assertEquals(409, duplicate.getStatusCode().value());
        assertEquals(BusinessCode.CONFLICT.code(), duplicate.getBody().code());
        assertFalse(duplicate.getBody().msg().contains("SQL"));
        assertEquals(422, invalidReference.getStatusCode().value());
        assertEquals(
                BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION.code(),
                invalidReference.getBody().code());
        assertFalse(invalidReference.getBody().msg().contains("SQL"));
    }
}
