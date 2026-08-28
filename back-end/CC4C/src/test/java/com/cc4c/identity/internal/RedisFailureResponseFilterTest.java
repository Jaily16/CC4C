package com.cc4c.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void convertsWrappedRedisFailuresButRethrowsUnrelatedRuntimeFailures() throws Exception {
        RedisFailureResponseFilter filter = new RedisFailureResponseFilter(
                new SecurityErrorWriter(new ObjectMapper()));
        MockHttpServletResponse redisResponse = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                redisResponse,
                (request, servletResponse) -> {
                    throw new QueryTimeoutException(
                            "outer timeout",
                            new RedisSystemException(
                                    "redis unavailable",
                                    new IllegalStateException("test cause")));
                });

        assertEquals(503, redisResponse.getStatus());
        assertTrue(redisResponse.getContentAsString().contains("\"code\":50300"));

        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, servletResponse) -> {
                    throw new IllegalStateException("unrelated failure");
                }));
    }
}
