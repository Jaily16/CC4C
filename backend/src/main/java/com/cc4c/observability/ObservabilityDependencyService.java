package com.cc4c.observability;

import com.cc4c.observability.ObservabilityDtos.DependenciesResponse;
import com.cc4c.observability.ObservabilityDtos.DependencyItemResponse;
import com.cc4c.shared.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

/** ObservabilityDependencyService 直接汇总健康贡献者及应用可用性，只返回归一化状态。 */
@Service
public final class ObservabilityDependencyService {
    private static final Set<String> SAFE_STATUSES = Set.of("UP", "DOWN", "OUT_OF_SERVICE", "DEGRADED", "UNKNOWN");

    private final HealthContributorRegistry healthRegistry;
    private final ApplicationAvailability availability;
    private final PrometheusClient prometheus;

    ObservabilityDependencyService(
            HealthContributorRegistry healthRegistry,
            ApplicationAvailability availability,
            PrometheusClient prometheus) {
        this.healthRegistry = healthRegistry;
        this.availability = availability;
        this.prometheus = prometheus;
    }

    public DependenciesResponse snapshot(HttpServletRequest request) {
        String liveness = availability.getLivenessState() == LivenessState.CORRECT ? "UP" : "DOWN";
        String readiness = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC ? "UP" : "DOWN";
        List<DependencyItemResponse> items = new ArrayList<>();
        items.add(item("database", "MySQL", contributorStatus("db")));
        items.add(item("redis", "Redis Session", contributorStatus("securityRedis")));
        items.add(item("cache", "业务缓存", contributorStatus("businessCache")));
        items.add(item("rabbitmq", "RabbitMQ", contributorStatus("rabbit")));
        items.add(item("async-messaging", "异步消息", contributorStatus("asyncMessaging")));
        items.add(item("prometheus", "Prometheus", prometheus.ready() ? "UP" : "DOWN"));

        boolean coreDown = "DOWN".equals(liveness)
                || "DOWN".equals(readiness)
                || items.stream()
                        .filter(item -> Set.of("database", "redis").contains(item.id()))
                        .anyMatch(item -> Set.of("DOWN", "OUT_OF_SERVICE").contains(item.status()));
        boolean degraded = items.stream().anyMatch(item -> !"UP".equals(item.status()));
        Object correlation = request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE);
        String requestId = correlation instanceof String value ? value : CorrelationIds.normalizeOrGenerate(null);
        return new DependenciesResponse(
                coreDown ? "DOWN" : degraded ? "DEGRADED" : "UP",
                Instant.now(),
                requestId,
                liveness,
                readiness,
                List.copyOf(items));
    }

    private DependencyItemResponse item(String id, String title, String status) {
        String message =
                switch (status) {
                    case "UP" -> "正常";
                    case "DEGRADED" -> "部分能力降级";
                    case "DOWN", "OUT_OF_SERVICE" -> "当前不可用";
                    default -> "状态未知";
                };
        return new DependencyItemResponse(id, title, status, message);
    }

    private String contributorStatus(String name) {
        HealthContributor contributor = healthRegistry.getContributor(name);
        if (contributor == null) {
            return "UNKNOWN";
        }
        if (contributor instanceof HealthIndicator indicator) {
            return normalize(indicator.health().getStatus().getCode());
        }
        if (contributor instanceof CompositeHealthContributor composite) {
            List<String> statuses = new ArrayList<>();
            composite.forEach(named -> {
                if (named.getContributor() instanceof HealthIndicator indicator) {
                    statuses.add(normalize(indicator.health().getStatus().getCode()));
                }
            });
            if (statuses.stream()
                    .anyMatch(status -> Set.of("DOWN", "OUT_OF_SERVICE").contains(status))) {
                return "DOWN";
            }
            if (statuses.stream().anyMatch(status -> !"UP".equals(status))) {
                return "DEGRADED";
            }
            return statuses.isEmpty() ? "UNKNOWN" : "UP";
        }
        return "UNKNOWN";
    }

    private String normalize(String candidate) {
        String upper = candidate == null ? "UNKNOWN" : candidate.toUpperCase(Locale.ROOT);
        return SAFE_STATUSES.contains(upper) ? upper : "UNKNOWN";
    }
}
