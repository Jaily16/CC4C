package com.cc4c.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdsTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesAllowlistedClientIdentifier() {
        String requestId = "client_request_123456";
        assertEquals(requestId, CorrelationIds.normalize(requestId, null));
    }

    @Test
    void replacesInvalidIdentifierAndRestoresNestedMdc() {
        String generated = CorrelationIds.normalize("contains sensitive spaces", null);
        assertNotEquals("contains sensitive spaces", generated);
        assertTrue(generated.matches("[A-Za-z0-9_-]{16,64}"));

        MDC.put(CorrelationIds.MDC_KEY, "outer-request-1234");
        try (CorrelationIds.Scope ignored = CorrelationIds.open("inner-request-1234")) {
            assertEquals("inner-request-1234", MDC.get(CorrelationIds.MDC_KEY));
        }
        assertEquals("outer-request-1234", MDC.get(CorrelationIds.MDC_KEY));
    }
}
