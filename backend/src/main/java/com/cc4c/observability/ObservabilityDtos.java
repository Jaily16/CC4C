package com.cc4c.observability;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** ObservabilityDtos 定义独立观测接口的脱敏、只读传输契约。 */
public final class ObservabilityDtos {
    private ObservabilityDtos() {}

    /** SourceStatus 表示固定数据源整体可用性，不携带内部异常。 */
    public enum SourceStatus {
        AVAILABLE,
        PARTIAL,
        EMPTY,
        UNAVAILABLE
    }

    /** PanelStatus 表示单个固定面板的查询结果。 */
    public enum PanelStatus {
        OK,
        EMPTY,
        ERROR
    }

    /** LoginRequest 仅接收配置账户凭据，密码不会进入响应或日志。 */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 1, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /** ObservabilityCsrfResponse 向内存中的前端客户端交付专用请求头名称和一次 CSRF Token。 */
    public record ObservabilityCsrfResponse(String headerName, String token) {}

    /** SessionResponse 描述独立观测 Session，不暴露 Cookie 或 Redis 标识。 */
    public record SessionResponse(
            boolean authenticated, String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /** LoginResponse 确认登录结果及当前 Session 期限。 */
    public record LoginResponse(String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /** MetricCardResponse 表示总览中的一个固定指标。 */
    public record MetricCardResponse(
            String id, String title, String unit, PanelStatus status, Double value, String message) {}

    /** OverviewResponse 汇总八个固定运行指标。 */
    public record OverviewResponse(Instant generatedAt, SourceStatus sourceStatus, List<MetricCardResponse> metrics) {}

    /** PointResponse 表示毫秒时间戳和可空数值；非有限值统一转换为空。 */
    public record PointResponse(long timestamp, Double value) {}

    /** SeriesResponse 只包含 catalog 允许的标签和有界点集。 */
    public record SeriesResponse(String name, Map<String, String> labels, List<PointResponse> points) {}

    /** PanelResponse 表示固定面板、脱敏状态和最多四组查询结果。 */
    public record PanelResponse(
            String id,
            String title,
            String unit,
            PanelStatus status,
            List<SeriesResponse> series,
            boolean truncated,
            String message) {}

    /** DashboardResponse 表示一个固定 Dashboard 在受限时间范围内的结果。 */
    public record DashboardResponse(
            String id,
            String title,
            String range,
            long stepSeconds,
            Instant generatedAt,
            SourceStatus sourceStatus,
            List<PanelResponse> panels) {}

    /** AlertRuleResponse 将规则状态映射为固定中文元数据，不返回 PromQL 或 lastError。 */
    public record AlertRuleResponse(
            String id,
            String title,
            String description,
            String severity,
            String state,
            String health,
            Instant lastEvaluationAt,
            String message) {}

    /** AlertsResponse 汇总 catalog 中精确二十条告警。 */
    public record AlertsResponse(
            Instant generatedAt,
            SourceStatus sourceStatus,
            int expected,
            int loaded,
            int firing,
            int pending,
            List<AlertRuleResponse> rules) {}

    /** DependencyItemResponse 只公开依赖名称和归一化状态。 */
    public record DependencyItemResponse(String id, String title, String status, String message) {}

    /** DependenciesResponse 汇总应用可用性、依赖与请求关联标识。 */
    public record DependenciesResponse(
            String overall,
            Instant checkedAt,
            String requestId,
            String liveness,
            String readiness,
            List<DependencyItemResponse> dependencies) {}
}
