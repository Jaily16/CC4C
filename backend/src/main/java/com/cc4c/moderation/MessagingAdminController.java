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

@Validated
@RestController
@RequestMapping("/admin/messaging/messages")
@PreAuthorize("hasRole('ADMIN')")
/** MessagingAdminController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class MessagingAdminController {
    private final AsyncMessageOperations operations;

    MessagingAdminController(AsyncMessageOperations operations) {
        this.operations = operations;
    }

    @GetMapping
    @Operation(summary = "List safe asynchronous message summaries")
    public ApiResponse<PageResponse<AsyncMessageSummary>> messages(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(operations.find(status, eventType, new PageQuery(page, size))));
    }

    @PostMapping("/{eventId}/retry")
    @Operation(summary = "Retry a recoverable asynchronous message as a new generation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Retry accepted")
    public ResponseEntity<ApiResponse<Boolean>> retry(
            @PathVariable @Pattern(regexp = "[0-9a-fA-F-]{36}") String eventId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(operations.retry(eventId)));
    }

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
