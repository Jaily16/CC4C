package com.cc4c.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityAssetsTest {
    private static final Path ROOT = Path.of("..", "..", "observability").normalize();

    @Test
    void prometheusAndGrafanaYamlAssetsAreSyntacticallyValid() throws IOException {
        List<Path> yamlFiles = List.of(
                ROOT.resolve("prometheus/prometheus.yml.template"),
                ROOT.resolve("prometheus/rules/cc4c-alerts.yml"),
                ROOT.resolve("prometheus/tests/cc4c-alerts.test.yml"),
                ROOT.resolve("grafana/provisioning/datasources/prometheus.yml"),
                ROOT.resolve("grafana/provisioning/dashboards/cc4c.yml"));
        Yaml parser = new Yaml();
        for (Path file : yamlFiles) {
            assertTrue(Files.isRegularFile(file), () -> "Missing observability asset: " + file);
            assertNotNull(parser.load(Files.readString(file)), () -> "Empty YAML asset: " + file);
        }

        String template = Files.readString(ROOT.resolve("prometheus/prometheus.yml.template"));
        assertTrue(template.contains("127.0.0.1:4081"));
        assertTrue(template.contains("127.0.0.1:15692"));
        assertFalse(template.contains("password: replace-with"));
        assertFalse(template.contains("redis://"));
        assertFalse(template.contains("amqp://"));
    }

    @Test
    void alertRulesContainTheLockedLowCardinalitySet() throws IOException {
        Map<?, ?> document = new Yaml().load(
                Files.readString(ROOT.resolve("prometheus/rules/cc4c-alerts.yml")));
        List<?> groups = (List<?>) document.get("groups");
        Set<String> alertNames = new HashSet<>();
        for (Object rawGroup : groups) {
            Map<?, ?> group = (Map<?, ?>) rawGroup;
            for (Object rawRule : (List<?>) group.get("rules")) {
                Map<?, ?> rule = (Map<?, ?>) rawRule;
                alertNames.add(String.valueOf(rule.get("alert")));
            }
        }
        assertEquals(20, alertNames.size());
        assertTrue(alertNames.containsAll(Set.of(
                "Cc4cBackendUnreachable",
                "Cc4cHttp5xxRateHigh",
                "Cc4cApiP95High",
                "Cc4cHikariPending",
                "Cc4cHikariUtilizationHigh",
                "Cc4cMybatisP95High",
                "Cc4cMybatisErrorRateHigh",
                "Cc4cCacheHitRatioLow",
                "Cc4cCacheFallbackIncreasing",
                "Cc4cAuthenticationFailuresHigh",
                "Cc4cRateLimitRejectionsHigh",
                "Cc4cOutboxBacklogOld",
                "Cc4cOutboxFailed",
                "Cc4cMessagingSamplerStale",
                "Cc4cRabbitBacklog",
                "Cc4cRabbitNoConsumers",
                "Cc4cRabbitDeadLetters",
                "Cc4cJvmHeapHigh",
                "Cc4cProcessCpuHigh",
                "Cc4cGcPauseP99High")));
    }

    @Test
    void dashboardsHaveStableUniqueUidsAndNoEmbeddedSecrets() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Path> files;
        try (var stream = Files.list(ROOT.resolve("grafana/dashboards"))) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertEquals(3, files.size());
        Set<String> uids = new HashSet<>();
        for (Path file : files) {
            String content = Files.readString(file);
            JsonNode dashboard = objectMapper.readTree(content);
            assertTrue(uids.add(dashboard.path("uid").asText()));
            assertTrue(dashboard.path("panels").size() >= 6);
            assertFalse(content.contains("password"));
            assertFalse(content.contains("redis://"));
            assertFalse(content.contains("amqp://"));
        }
        assertEquals(Set.of(
                "cc4c-api-jvm",
                "cc4c-db-cache-security",
                "cc4c-messaging"), uids);
    }
}
