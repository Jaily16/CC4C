package com.cc4c.observability;

import com.cc4c.observability.ObservabilityDtos.AlertsResponse;
import com.cc4c.observability.ObservabilityDtos.DashboardResponse;
import com.cc4c.observability.ObservabilityDtos.DependenciesResponse;
import com.cc4c.observability.ObservabilityDtos.OverviewResponse;
import com.cc4c.shared.ApiResponse;
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

/** ObservabilityDataController 暴露固定总览、面板、告警和依赖只读接口。 */
@Validated
@RestController
@RequestMapping("/observability/api")
@PreAuthorize("hasRole('OBSERVABILITY')")
public class ObservabilityDataController {
    private final ObservabilityQueryService queryService;
    private final ObservabilityDependencyService dependencyService;

    ObservabilityDataController(
            ObservabilityQueryService queryService, ObservabilityDependencyService dependencyService) {
        this.queryService = queryService;
        this.dependencyService = dependencyService;
    }

    @GetMapping("/overview")
    @Operation(summary = "读取八项固定观测总览")
    public ApiResponse<OverviewResponse> overview() {
        return ApiResponse.success(queryService.overview());
    }

    @GetMapping("/dashboard/{dashboardId}")
    @Operation(summary = "读取固定观测 Dashboard")
    public ApiResponse<DashboardResponse> dashboard(
            @PathVariable @Pattern(regexp = "api-jvm|data-cache-security|messaging") String dashboardId,
            @RequestParam(defaultValue = "1h") @Pattern(regexp = "15m|1h|6h|24h") String range) {
        return ApiResponse.success(queryService.dashboard(dashboardId, range));
    }

    @GetMapping("/alerts")
    @Operation(summary = "读取二十条固定 Prometheus 告警")
    public ApiResponse<AlertsResponse> alerts() {
        return ApiResponse.success(queryService.alerts());
    }

    @GetMapping("/dependencies")
    @Operation(summary = "读取脱敏依赖及应用可用性")
    public ApiResponse<DependenciesResponse> dependencies(HttpServletRequest request) {
        return ApiResponse.success(dependencyService.snapshot(request));
    }
}
