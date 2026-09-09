package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.AsyncMessageSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResponse;
import com.cc4c.support.messaging.AsyncMessageOperations;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 向 ADMIN 提供异步消息摘要查询及逐事件恢复、忽略操作。 */
@Validated
@RestController
@RequestMapping("/admin/messaging/messages")
@PreAuthorize("hasRole('ADMIN')")
public class MessagingAdminController {
    private final AsyncMessageOperations operations;

    /**
     * 接入可靠消息查询及人工操作服务。
     *
     * @param operations 消息摘要查询及人工恢复服务
     */
    MessagingAdminController(AsyncMessageOperations operations) {
        this.operations = operations;
    }

    /**
     * 按可选状态和事件类型分页查询不含载荷的消息摘要。
     *
     * @param status 可选消息状态筛选条件
     * @param eventType 可选事件类型筛选条件
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 消息摘要分页响应
     */
    @GetMapping
    @Operation(summary = "List safe asynchronous message summaries")
    public ApiResponse<PageResponse<AsyncMessageSummary>> messages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(operations.find(status, eventType, new PageQuery(page, size))));
    }

    /**
     * 申请恢复指定事件，恢复条件交给操作服务校验；接收成功返回 HTTP 202。
     *
     * @param eventId 36 字符事件 ID，仅允许十六进制字符和连字符
     * @return 事件恢复申请结果
     */
    @PostMapping("/{eventId}/retry")
    @Operation(summary = "Retry a recoverable asynchronous message as a new generation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Retry accepted")
    public ResponseEntity<ApiResponse<Boolean>> retry(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(operations.retry(eventId)));
    }

    /**
     * 从管理员认证名提取操作者 ID，委托服务忽略指定失败事件。
     *
     * @param eventId 36 字符事件 ID，仅允许十六进制字符和连字符
     * @param authentication 当前已认证的管理员身份
     * @return 事件忽略结果
     */
    @PostMapping("/{eventId}/ignore")
    @Operation(summary = "Ignore a failed asynchronous message")
    public ApiResponse<Boolean> ignore(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId, Authentication authentication) {
        String principalName = authentication.getName();
        String actorId =
                principalName.startsWith("ADMIN:") ? principalName.substring("ADMIN:".length()) : principalName;
        return ApiResponse.success(operations.ignore(eventId, actorId));
    }
}
