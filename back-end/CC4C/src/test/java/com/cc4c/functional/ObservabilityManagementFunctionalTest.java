package com.cc4c.functional;

import com.cc4c.support.Cc4cTestInfrastructure;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "cc4c.observability.enabled=true",
                "cc4c.observability.messaging-sample-interval=1s",
                "spring.rabbitmq.dynamic=false"
        })
@AutoConfigureObservability
@ActiveProfiles("test")
@Import(Cc4cTestInfrastructure.class)
class ObservabilityManagementFunctionalTest {
    private static final String NAMESPACE =
            "cc4c:test:observability:" + UUID.randomUUID().toString().replace("-", "");

    @LocalManagementPort
    private int managementPort;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url",
                Cc4cTestInfrastructure::securityRedisUrl);
        registry.add("cc4c.cache.redis-url",
                Cc4cTestInfrastructure::cacheRedisUrl);
        registry.add("cc4c.cache.namespace", () -> NAMESPACE + ":cache");
        registry.add("spring.session.redis.namespace", () -> NAMESPACE + ":session");
        registry.add("cc4c.security.key-prefix", () -> NAMESPACE + ":security");
        Cc4cTestInfrastructure.registerRabbitProperties(registry);
        registry.add("cc4c.messaging.namespace", () -> NAMESPACE + ":messaging");
    }

    @Test
    void exposesOnlyHealthSummaryAnonymously() {
        TestRestTemplate anonymous = new TestRestTemplate();
        assertEquals(HttpStatus.OK,
                anonymous.getForEntity(url("/actuator/health"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                anonymous.getForEntity(url("/actuator/health/liveness"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                anonymous.getForEntity(url("/actuator/health/readiness"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                anonymous.exchange(
                        url("/actuator/health"),
                        HttpMethod.HEAD,
                        null,
                        Void.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                anonymous.getForEntity(url("/actuator/prometheus"), String.class).getStatusCode());
        TestRestTemplate observer = new TestRestTemplate(
                "cc4c_test_observer", "cc4c-test-observer-password-not-secret");
        assertEquals(HttpStatus.UNAUTHORIZED,
                observer.getForEntity(url("/actuator/env"), String.class).getStatusCode());
    }

    @Test
    void observabilityBasicIdentityCanReadMetricsAndDependencies() {
        TestRestTemplate observer = new TestRestTemplate(
                "cc4c_test_observer", "cc4c-test-observer-password-not-secret");
        ResponseEntity<String> prometheus = observer.getForEntity(
                url("/actuator/prometheus"), String.class);
        assertEquals(HttpStatus.OK, prometheus.getStatusCode());
        assertTrue(prometheus.getBody().contains("jvm_memory_used_bytes"));

        ResponseEntity<String> dependencies = observer.getForEntity(
                url("/actuator/health/dependencies"), String.class);
        assertEquals(HttpStatus.OK, dependencies.getStatusCode());
        assertFalse(dependencies.getBody().contains("redis://"));
        assertFalse(dependencies.getBody().contains("jdbc:mysql"));
    }

    @Test
    void incorrectBasicCredentialsRemainIsolatedFromBusinessSecurity() {
        TestRestTemplate wrong = new TestRestTemplate("admin", "not-the-management-password");
        ResponseEntity<String> response = wrong.getForEntity(
                url("/actuator/prometheus"), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("WWW-Authenticate")
                .contains("cc4c-observability"));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + managementPort + path;
    }

}
