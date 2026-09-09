package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.ObservabilityDtos.AlertsResponse;
import com.cc4c.dto.ObservabilityDtos.DashboardResponse;
import com.cc4c.dto.ObservabilityDtos.DependenciesResponse;
import com.cc4c.dto.ObservabilityDtos.OverviewResponse;
import com.cc4c.service.ObservabilityDependencyService;
import com.cc4c.service.ObservabilityQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 向 OBSERVABILITY 角色提供固定总览、Dashboard、告警和依赖检查。 */
@Validated
@RestController
@RequestMapping("/observability/api")
@PreAuthorize("hasRole('OBSERVABILITY')")
public class ObservabilityDataController {
    private final ObservabilityQueryService queryService;
    private final ObservabilityDependencyService dependencyService;

    /**
     * 接入固定指标查询和依赖健康检查服务。
     *
     * @param queryService 固定观测查询服务
     * @param dependencyService 依赖健康快照服务
     */
    ObservabilityDataController(
            ObservabilityQueryService queryService, ObservabilityDependencyService dependencyService) {
        this.queryService = queryService;
        this.dependencyService = dependencyService;
    }

    /**
     * 查询固定八项总览指标及数据源状态。
     *
     * @return 总览指标响应
     */
    @GetMapping("/overview")
    @Operation(summary = "读取八项固定观测总览")
    public ApiResponse<OverviewResponse> overview() {
        return ApiResponse.success(queryService.overview());
    }

    /**
     * 限制 Dashboard ID 和时间范围后执行固定查询。
     *
     * @param dashboardId api-jvm、data-cache-security 或 messaging
     * @param range 15m、1h、6h 或 24h，默认 1h
     * @return 指定 Dashboard 的面板结果
     */
    @GetMapping("/dashboard/{dashboardId}")
    @Operation(summary = "读取固定观测 Dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @PathVariable @Pattern(regexp = "api-jvm|data-cache-security|messaging") String dashboardId,
            @RequestParam(defaultValue = "1h") @Pattern(regexp = "15m|1h|6h|24h") String range) {
        return ApiResponse.success(queryService.dashboard(dashboardId, range));
    }

    /**
     * 读取固定二十条告警的加载和评估状态。
     *
     * @return 告警规则汇总响应
     */
    @GetMapping("/alerts")
    @Operation(summary = "读取二十条固定 Prometheus 告警")
    public ApiResponse<AlertsResponse> alerts() {
        return ApiResponse.success(queryService.alerts());
    }

    /**
     * 根据当前请求生成带关联 ID 的依赖健康快照。
     *
     * @param request 当前 HTTP 请求，提供会话和安全校验上下文
     * @return 脱敏依赖及应用可用性响应
     */
    @GetMapping("/dependencies")
    @Operation(summary = "读取脱敏依赖及应用可用性")
    public ApiResponse<DependenciesResponse> dependencies(HttpServletRequest request) {
        return ApiResponse.success(dependencyService.snapshot(request));
    }
}
