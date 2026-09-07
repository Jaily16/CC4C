package com.cc4c.moderation;

import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.AsyncMessageOperations;
import com.cc4c.shared.AsyncMessageSummary;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResponse;
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

/**
 * MessagingAdminController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Validated
@RestController
@RequestMapping("/admin/messaging/messages")
@PreAuthorize("hasRole('ADMIN')")
public class MessagingAdminController {
    private final AsyncMessageOperations operations;

    /**
     * 创建 MessagingAdminController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param operations 由容器注入的 AsyncMessageOperations 协作组件
     */
    MessagingAdminController(AsyncMessageOperations operations) {
        this.operations = operations;
    }

    /**
     * 执行 MessagingAdminController 中的 messages 职责，并保持既有权限、事务与副作用边界。
     *
     * @param status 调用方提供的 {@code status} 值
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 MessagingAdminController 中的 retry 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/{eventId}/retry")
    @Operation(summary = "Retry a recoverable asynchronous message as a new generation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Retry accepted")
    public ResponseEntity<ApiResponse<Boolean>> retry(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(operations.retry(eventId)));
    }

    /**
     * 执行 MessagingAdminController 中的 ignore 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventId 目标对象的稳定标识
     * @param authentication 调用方提供的 {@code authentication} 值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
