package com.cc4c.modularity;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
abstract class ModuleTestSupport {

    @MockitoBean
    JavaMailSender javaMailSender;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("CC4C_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> required("CC4C_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("CC4C_TEST_DB_PASSWORD"));
        registry.add("spring.data.redis.url", () -> required("CC4C_TEST_REDIS_URL"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test database environment variable is missing: " + name);
        }
        return value;
    }
}
