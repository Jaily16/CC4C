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

/**
 * 协调独立观测门户用例及其持久化、安全和外部协作边界。
 */
@Service
public final class ObservabilityDependencyService {
    private static final Set<String> SAFE_STATUSES = Set.of("UP", "DOWN", "OUT_OF_SERVICE", "DEGRADED", "UNKNOWN");

    private final HealthContributorRegistry healthRegistry;
    private final ApplicationAvailability availability;
    private final PrometheusClient prometheus;

    /**
     * 创建 ObservabilityDependencyService 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param healthRegistry 调用方提供的 {@code healthRegistry} 值
     * @param availability 调用方提供的 {@code availability} 值
     * @param prometheus 由容器注入的 PrometheusClient 协作组件
     */
    ObservabilityDependencyService(
            HealthContributorRegistry healthRegistry,
            ApplicationAvailability availability,
            PrometheusClient prometheus) {
        this.healthRegistry = healthRegistry;
        this.availability = availability;
        this.prometheus = prometheus;
    }

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 当前操作产生的 DependenciesResponse 结果
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param status 当前对象或流程的有限状态
     * @return 当前操作产生的 DependencyItemResponse 结果
     */
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

    /**
     * 执行观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param name 调用方提供的 {@code name} 值
     * @return 按当前协议生成或读取的字符串值
     */
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

    /**
     * 规范化观测门户数据，保持独立身份、查询白名单和脱敏失败状态。
     *
     * @param candidate 调用方提供的 {@code candidate} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private String normalize(String candidate) {
        String upper = candidate == null ? "UNKNOWN" : candidate.toUpperCase(Locale.ROOT);
        return SAFE_STATUSES.contains(upper) ? upper : "UNKNOWN";
    }
}
