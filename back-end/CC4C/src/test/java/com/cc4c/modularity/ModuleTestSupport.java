package com.cc4c.modularity;

import com.cc4c.shared.MessagingTopology;
import com.cc4c.support.RabbitTestResources;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ModuleTestSupport {
    private static final String CACHE_NAMESPACE =
            "cc4c:test:module:" + UUID.randomUUID().toString().replace("-", "") + ":cache";
    private static final String RABBIT_NAMESPACE =
            "cc4c.test.messaging.module." + UUID.randomUUID().toString().replace("-", "");

    @MockitoBean
    JavaMailSender javaMailSender;

    @Autowired
    private AmqpAdmin rabbitAdmin;

    @Autowired
    private MessagingTopology messagingTopology;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("CC4C_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> required("CC4C_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("CC4C_TEST_DB_PASSWORD"));
        registry.add("spring.data.redis.url", () -> required("CC4C_TEST_REDIS_URL"));
        registry.add("cc4c.cache.redis-url", () -> required("CC4C_TEST_CACHE_REDIS_URL"));
        registry.add("cc4c.cache.namespace", () -> CACHE_NAMESPACE);
        registry.add("spring.rabbitmq.addresses", () -> required("CC4C_TEST_RABBITMQ_URL"));
        registry.add("cc4c.messaging.namespace", () -> RABBIT_NAMESPACE);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test database environment variable is missing: " + name);
        }
        return value;
    }

    @AfterAll
    void removeOnlyThisTestNamespaceFromRabbit() {
        RabbitTestResources.deleteKnownNamespaceResources(rabbitAdmin, messagingTopology);
    }
}
