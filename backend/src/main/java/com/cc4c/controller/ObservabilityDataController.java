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

/**
 * 提供独立观测门户 HTTP 接口，完成输入校验、权限边界和统一响应封装。
 */
@Validated
@RestController
@RequestMapping("/observability/api")
@PreAuthorize("hasRole('OBSERVABILITY')")
public class ObservabilityDataController {
    private final ObservabilityQueryService queryService;
    private final ObservabilityDependencyService dependencyService;

    /**
     * 创建 ObservabilityDataController 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param queryService 由容器注入的 ObservabilityQueryService 协作组件
     * @param dependencyService 由容器注入的 ObservabilityDependencyService 协作组件
     */
    ObservabilityDataController(
            ObservabilityQueryService queryService, ObservabilityDependencyService dependencyService) {
        this.queryService = queryService;
        this.dependencyService = dependencyService;
    }

    /**
     * 处理 {@code overview} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @return 统一封装且可安全返回客户端的响应
     */
    @GetMapping("/overview")
    @Operation(summary = "读取八项固定观测总览")
    public ApiResponse<OverviewResponse> overview() {
        return ApiResponse.success(queryService.overview());
    }

    /**
     * 处理 {@code dashboard} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param dashboardId 目标对象的稳定标识
     * @param range 调用方提供的 {@code range} 值
     * @return 统一封装且可安全返回客户端的响应
     */
    @GetMapping("/dashboard/{dashboardId}")
    @Operation(summary = "读取固定观测 Dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @PathVariable @Pattern(regexp = "api-jvm|data-cache-security|messaging") String dashboardId,
            @RequestParam(defaultValue = "1h") @Pattern(regexp = "15m|1h|6h|24h") String range) {
        return ApiResponse.success(queryService.dashboard(dashboardId, range));
    }

    /**
     * 处理 {@code alerts} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @return 统一封装且可安全返回客户端的响应
     */
    @GetMapping("/alerts")
    @Operation(summary = "读取二十条固定 Prometheus 告警")
    public ApiResponse<AlertsResponse> alerts() {
        return ApiResponse.success(queryService.alerts());
    }

    /**
     * 处理 {@code dependencies} 对应的 HTTP 请求，委托业务边界并返回统一响应。
     *
     * @param request 当前 HTTP 请求，仅用于读取受控请求信息
     * @return 统一封装且可安全返回客户端的响应
     */
    @GetMapping("/dependencies")
    @Operation(summary = "读取脱敏依赖及应用可用性")
    public ApiResponse<DependenciesResponse> dependencies(HttpServletRequest request) {
        return ApiResponse.success(dependencyService.snapshot(request));
    }
}
