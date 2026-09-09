package com.cc4c.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 集中声明独立观测门户接口请求与响应的数据结构，不承载业务流程。
 */
public final class ObservabilityDtos {
    /** 仅作为嵌套 DTO 的命名容器，禁止外部实例化。 */
    private ObservabilityDtos() {}

    /** 标记观测数据源为可用、部分可用、空结果或不可用。 */
    public enum SourceStatus {
        AVAILABLE,
        PARTIAL,
        EMPTY,
        UNAVAILABLE
    }

    /** 区分面板查询成功、无样本和查询错误，避免把无数据当作零值。 */
    public enum PanelStatus {
        OK,
        EMPTY,
        ERROR
    }

    /**
     * 独立观测门户的用户名和密码请求；与业务账户登录模型分离。
     *
     * @param username 观测登录用户名
     * @param password 观测登录明文密码，不得记录
     */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 1, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /**
     * 向观测页面提供后续写请求需要的 CSRF 请求头名称和令牌。
     *
     * @param headerName 写请求应携带的 CSRF 请求头名称
     * @param token CSRF 令牌，不得记录
     */
    public record ObservabilityCsrfResponse(String headerName, String token) {}

    /**
     * 返回观测身份是否已认证及空闲、绝对过期时间。
     *
     * @param authenticated 是否已通过观测认证
     * @param role 观测身份角色
     * @param username 观测登录用户名
     * @param idleExpiresAt 观测会话空闲过期时间
     * @param absoluteExpiresAt 观测会话绝对过期时间
     */
    public record SessionResponse(
            boolean authenticated, String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * 观测登录成功后的角色、用户名及两类会话过期时间。
     *
     * @param role 观测身份角色
     * @param username 观测登录用户名
     * @param idleExpiresAt 观测会话空闲过期时间
     * @param absoluteExpiresAt 观测会话绝对过期时间
     */
    public record LoginResponse(String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * 总览指标卡的当前值与查询状态；无有效值时不以零代替。
     *
     * @param id 预定义观测对象标识
     * @param title 观测对象展示标题
     * @param unit 面板配置中的展示单位
     * @param status 面板查询状态
     * @param value 采样数值，无有效数值时为空
     * @param message 当前查询或检查的可展示说明
     */
    public record MetricCardResponse(
            String id, String title, String unit, PanelStatus status, Double value, String message) {}

    /**
     * 聚合总览指标卡及本次生成时间和数据源状态。
     *
     * @param generatedAt 本次响应生成时间
     * @param sourceStatus 聚合后的数据源可用性
     * @param metrics 总览指标卡列表
     */
    public record OverviewResponse(Instant generatedAt, SourceStatus sourceStatus, List<MetricCardResponse> metrics) {}

    /**
     * 时序图中的单个采样点；无效数值通过空值表达。
     *
     * @param timestamp Unix 时间戳，单位毫秒
     * @param value 采样数值，无有效数值时为空
     */
    public record PointResponse(long timestamp, Double value) {}

    /**
     * 带名称和标签集合的时序数据，供面板区分多条曲线。
     *
     * @param name 时间序列名称
     * @param labels 时间序列的标签键值对
     * @param points 时间序列采样点
     */
    public record SeriesResponse(String name, Map<String, String> labels, List<PointResponse> points) {}

    /**
     * 单个观测面板的曲线与状态，同时说明结果是否受限截断。
     *
     * @param id 预定义观测对象标识
     * @param title 观测对象展示标题
     * @param unit 面板配置中的展示单位
     * @param status 面板查询状态
     * @param series 面板时间序列列表
     * @param truncated 结果是否因查询上限被截断
     * @param message 当前查询或检查的可展示说明
     */
    public record PanelResponse(
            String id,
            String title,
            String unit,
            PanelStatus status,
            List<SeriesResponse> series,
            boolean truncated,
            String message) {}

    /**
     * 固定 Dashboard 在指定时间范围内的面板结果与采样步长。
     *
     * @param id 预定义观测对象标识
     * @param title 观测对象展示标题
     * @param range 请求的固定时间范围
     * @param stepSeconds 采样步长，单位秒
     * @param generatedAt 本次响应生成时间
     * @param sourceStatus 聚合后的数据源可用性
     * @param panels Dashboard 面板列表
     */
    public record DashboardResponse(
            String id,
            String title,
            String range,
            long stepSeconds,
            Instant generatedAt,
            SourceStatus sourceStatus,
            List<PanelResponse> panels) {}

    /**
     * 展示一条预定义告警规则的状态、评估健康情况及最近评估时间。
     *
     * @param id 预定义观测对象标识
     * @param title 观测对象展示标题
     * @param description 告警规则说明
     * @param severity 告警严重级别
     * @param state 告警规则当前状态
     * @param health 规则评估健康状态
     * @param lastEvaluationAt 规则最近评估时间
     * @param message 当前查询或检查的可展示说明
     */
    public record AlertRuleResponse(
            String id,
            String title,
            String description,
            String severity,
            String state,
            String health,
            Instant lastEvaluationAt,
            String message) {}

    /**
     * 汇总预期和已加载规则数量，以及 firing、pending 数量与逐条状态。
     *
     * @param generatedAt 本次响应生成时间
     * @param sourceStatus 聚合后的数据源可用性
     * @param expected 预定义规则数量
     * @param loaded 实际加载规则数量
     * @param firing 正在触发的规则数量
     * @param pending 等待满足持续时间的规则数量
     * @param rules 逐条告警规则结果
     */
    public record AlertsResponse(
            Instant generatedAt,
            SourceStatus sourceStatus,
            int expected,
            int loaded,
            int firing,
            int pending,
            List<AlertRuleResponse> rules) {}

    /**
     * 依赖检查的单项状态与可展示说明，不返回连接凭据。
     *
     * @param id 预定义观测对象标识
     * @param title 观测对象展示标题
     * @param status 依赖健康状态
     * @param message 当前查询或检查的可展示说明
     */
    public record DependencyItemResponse(String id, String title, String status, String message) {}

    /**
     * 聚合存活、就绪及外部依赖检查结果，携带关联请求 ID。
     *
     * @param overall 聚合健康状态
     * @param checkedAt 依赖检查时间
     * @param requestId 本次检查的请求关联 ID
     * @param liveness 存活检查状态
     * @param readiness 就绪检查状态
     * @param dependencies 逐项依赖检查结果
     */
    public record DependenciesResponse(
            String overall,
            Instant checkedAt,
            String requestId,
            String liveness,
            String readiness,
            List<DependencyItemResponse> dependencies) {}
}
