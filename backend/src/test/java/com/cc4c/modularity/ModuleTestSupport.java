package com.cc4c.modularity;

import com.cc4c.shared.MessagingTopology;
import com.cc4c.support.Cc4cTestInfrastructure;
import com.cc4c.support.RabbitTestResources;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(Cc4cTestInfrastructure.class)
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
        registry.add("spring.data.redis.url", Cc4cTestInfrastructure::securityRedisUrl);
        registry.add("cc4c.cache.redis-url", Cc4cTestInfrastructure::cacheRedisUrl);
        registry.add("cc4c.cache.namespace", () -> CACHE_NAMESPACE);
        Cc4cTestInfrastructure.registerRabbitProperties(registry);
        registry.add("cc4c.messaging.namespace", () -> RABBIT_NAMESPACE);
    }

    @AfterAll
    void removeOnlyThisTestNamespaceFromRabbit() {
        RabbitTestResources.deleteKnownNamespaceResources(rabbitAdmin, messagingTopology);
    }
}
