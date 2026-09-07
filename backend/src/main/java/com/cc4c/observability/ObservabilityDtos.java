package com.cc4c.observability;

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
    /**
     * 创建 ObservabilityDtos 实例，不触发外部 I/O。
     */
    private ObservabilityDtos() {}

    /**
     * SourceStatus 枚举独立观测门户的有限状态或协议取值。
     */
    public enum SourceStatus {
        AVAILABLE,
        PARTIAL,
        EMPTY,
        UNAVAILABLE
    }

    /**
     * PanelStatus 枚举独立观测门户的有限状态或协议取值。
     */
    public enum PanelStatus {
        OK,
        EMPTY,
        ERROR
    }

    /**
     * 承载独立观测门户接口的输入字段与声明式校验约束。
     *
     * @param username 待认证或查询的账户名
     * @param password 仅用于当前安全校验的密码或密码摘要
     */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 1, max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param headerName 调用方提供的 {@code headerName} 值
     * @param token 当前协议使用且不得记录的安全令牌
     */
    public record ObservabilityCsrfResponse(String headerName, String token) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param authenticated 调用方提供的 {@code authenticated} 值
     * @param role 当前身份的固定角色
     * @param username 待认证或查询的账户名
     * @param idleExpiresAt 当前操作使用的时间点
     * @param absoluteExpiresAt 当前操作使用的时间点
     */
    public record SessionResponse(
            boolean authenticated, String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param role 当前身份的固定角色
     * @param username 待认证或查询的账户名
     * @param idleExpiresAt 当前操作使用的时间点
     * @param absoluteExpiresAt 当前操作使用的时间点
     */
    public record LoginResponse(String role, String username, Instant idleExpiresAt, Instant absoluteExpiresAt) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param unit 调用方提供的 {@code unit} 值
     * @param status 当前对象或流程的有限状态
     * @param value 待处理或存储的值
     * @param message 当前处理的消息或用户提示
     */
    public record MetricCardResponse(
            String id, String title, String unit, PanelStatus status, Double value, String message) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param generatedAt 当前操作使用的时间点
     * @param sourceStatus 调用方提供的 {@code sourceStatus} 值
     * @param metrics 调用方提供的 {@code metrics} 值
     */
    public record OverviewResponse(Instant generatedAt, SourceStatus sourceStatus, List<MetricCardResponse> metrics) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param timestamp 调用方提供的 {@code timestamp} 值
     * @param value 待处理或存储的值
     */
    public record PointResponse(long timestamp, Double value) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param name 调用方提供的 {@code name} 值
     * @param labels 调用方提供的 {@code labels} 值
     * @param points 调用方提供的 {@code points} 值
     */
    public record SeriesResponse(String name, Map<String, String> labels, List<PointResponse> points) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param unit 调用方提供的 {@code unit} 值
     * @param status 当前对象或流程的有限状态
     * @param series 调用方提供的 {@code series} 值
     * @param truncated 调用方提供的 {@code truncated} 值
     * @param message 当前处理的消息或用户提示
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
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param range 调用方提供的 {@code range} 值
     * @param stepSeconds 调用方提供的 {@code stepSeconds} 值
     * @param generatedAt 当前操作使用的时间点
     * @param sourceStatus 调用方提供的 {@code sourceStatus} 值
     * @param panels 调用方提供的 {@code panels} 值
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
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param description 面向用户展示的说明文本
     * @param severity 调用方提供的 {@code severity} 值
     * @param state 调用方提供的 {@code state} 值
     * @param health 调用方提供的 {@code health} 值
     * @param lastEvaluationAt 当前操作使用的时间点
     * @param message 当前处理的消息或用户提示
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
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param generatedAt 当前操作使用的时间点
     * @param sourceStatus 调用方提供的 {@code sourceStatus} 值
     * @param expected 调用方提供的 {@code expected} 值
     * @param loaded 调用方提供的 {@code loaded} 值
     * @param firing 调用方提供的 {@code firing} 值
     * @param pending 调用方提供的 {@code pending} 值
     * @param rules 调用方提供的 {@code rules} 值
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
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param id 调用方提供的 {@code id} 值
     * @param title 当前博客或课程的标题
     * @param status 当前对象或流程的有限状态
     * @param message 当前处理的消息或用户提示
     */
    public record DependencyItemResponse(String id, String title, String status, String message) {}

    /**
     * 承载独立观测门户接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param overall 调用方提供的 {@code overall} 值
     * @param checkedAt 当前操作使用的时间点
     * @param requestId 目标对象的稳定标识
     * @param liveness 调用方提供的 {@code liveness} 值
     * @param readiness 调用方提供的 {@code readiness} 值
     * @param dependencies 调用方提供的 {@code dependencies} 值
     */
    public record DependenciesResponse(
            String overall,
            Instant checkedAt,
            String requestId,
            String liveness,
            String readiness,
            List<DependencyItemResponse> dependencies) {}
}
