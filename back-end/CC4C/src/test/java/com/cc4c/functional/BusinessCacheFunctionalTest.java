package com.cc4c.functional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessCacheFunctionalTest extends FunctionalTestSupport {

    @Test
    void publicCoursePageUsesBusinessCacheAfterWarmup() throws Exception {
        businessCache.metrics().reset();

        mockMvc.perform(get("/courses/home").param("page", "1").param("size", "8"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/courses/home").param("page", "1").param("size", "8"))
                .andExpect(status().isOk());

        assertTrue(businessCache.metrics().snapshot().hits() >= 1);
        assertTrue(businessCache.metrics().snapshot().loads() >= 1);
    }

    @Test
    void verifiedBlogDetailIsCachedButPendingBlogRemainsPrivate() throws Exception {
        UserFixture writer = createUser();
        BlogFixture verified = createBlog(writer, 1);
        BlogFixture pending = createBlog(writer, 0);
        businessCache.metrics().reset();

        mockMvc.perform(get("/blogs/{id}", verified.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/blogs/{id}", verified.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/blogs/{id}", pending.id()).with(asUser(writer)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/blogs/{id}", pending.id()).with(anonymous()))
                .andExpect(status().isNotFound());

        assertTrue(businessCache.metrics().snapshot().hits() >= 1);
        assertTrue(businessCache.metrics().snapshot().negativeHits() >= 1);
    }
}
