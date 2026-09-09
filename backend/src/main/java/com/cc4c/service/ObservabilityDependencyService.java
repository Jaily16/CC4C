package com.cc4c.service;

import com.cc4c.common.CorrelationIds;
import com.cc4c.dto.ObservabilityDtos.DependenciesResponse;
import com.cc4c.dto.ObservabilityDtos.DependencyItemResponse;
import com.cc4c.support.monitoring.PrometheusClient;
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

/** 聚合应用可用性、固定依赖健康和 Prometheus 就绪状态，仅返回允许的状态及固定提示。 */
@Service
public final class ObservabilityDependencyService {
    private static final Set<String> SAFE_STATUSES = Set.of("UP", "DOWN", "OUT_OF_SERVICE", "DEGRADED", "UNKNOWN");

    private final HealthContributorRegistry healthRegistry;
    private final ApplicationAvailability availability;
    private final PrometheusClient prometheus;

    /**
     * 接入健康贡献者注册表、应用可用性和 Prometheus 探测客户端。
     *
     * @param healthRegistry 按名称查找健康贡献者的注册表
     * @param availability 应用存活及就绪状态
     * @param prometheus 固定查询及就绪探测的 Prometheus 客户端
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
     * 检查六项依赖；存活、就绪或核心数据库/Session Redis 故障判为 DOWN，其他非正常项判为 DEGRADED。
     *
     * @param request 当前 HTTP 请求，提供关联 ID 或会话和来源上下文
     * @return 带请求关联 ID 的依赖快照
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
     * 将依赖状态转换为固定中文提示，不返回健康详情中的内部错误。
     *
     * @param id 预定义依赖标识
     * @param title 依赖展示名称
     * @param status 已归一化的健康状态
     * @return 单项依赖展示结果
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
     * 读取指定健康贡献者；组合贡献者只聚合直接 HealthIndicator 子项，缺失或无法识别时返回 UNKNOWN。
     *
     * @param name 健康贡献者注册名称
     * @return 归一后的依赖状态
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
     * 将健康状态转为大写并限制在允许集合，其他值统一为 UNKNOWN。
     *
     * @param candidate 待归一化的健康状态
     * @return 允许的健康状态编码
     */
    private String normalize(String candidate) {
        String upper = candidate == null ? "UNKNOWN" : candidate.toUpperCase(Locale.ROOT);
        return SAFE_STATUSES.contains(upper) ? upper : "UNKNOWN";
    }
}
