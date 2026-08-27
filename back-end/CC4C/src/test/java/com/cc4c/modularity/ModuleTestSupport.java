package com.cc4c.modularity;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

@ActiveProfiles("test")
abstract class ModuleTestSupport {
    private static final String CACHE_NAMESPACE =
            "cc4c:test:module:" + UUID.randomUUID().toString().replace("-", "") + ":cache";

    @MockitoBean
    JavaMailSender javaMailSender;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("CC4C_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> required("CC4C_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("CC4C_TEST_DB_PASSWORD"));
        registry.add("spring.data.redis.url", () -> required("CC4C_TEST_REDIS_URL"));
        registry.add("cc4c.cache.redis-url", () -> required("CC4C_TEST_CACHE_REDIS_URL"));
        registry.add("cc4c.cache.namespace", () -> CACHE_NAMESPACE);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test database environment variable is missing: " + name);
        }
        return value;
    }
}
