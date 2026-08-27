package com.cc4c.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisFailureResponseFilterTest {

    @Test
    void convertsPreControllerRedisFailuresToTheStandard503JsonEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RedisFailureResponseFilter filter = new RedisFailureResponseFilter(
                new SecurityErrorWriter(new ObjectMapper()));

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (request, servletResponse) -> {
                    throw new RedisSystemException(
                            "redis unavailable", new IllegalStateException("test cause"));
                });

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":50300"));
        assertTrue(response.getContentAsString().contains("\"data\":false"));
    }
}
