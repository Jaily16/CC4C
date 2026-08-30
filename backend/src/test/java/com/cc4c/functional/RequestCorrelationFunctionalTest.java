package com.cc4c.functional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class RequestCorrelationFunctionalTest extends FunctionalTestSupport {
    @Test
    void preservesValidRequestIdOnSuccess() throws Exception {
        mockMvc.perform(get("/auth/session").header("X-Request-ID", "client-request-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "client-request-1234"));
    }

    @Test
    void generatesSafeRequestIdForSecurityAndMissingResourceResponses() throws Exception {
        mockMvc.perform(get("/users/me").header("X-Request-ID", "bad id"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-ID", matchesPattern("[A-Za-z0-9_-]{16,64}")));
        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-ID", matchesPattern("[A-Za-z0-9_-]{16,64}")));
    }
}
