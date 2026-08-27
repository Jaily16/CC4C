package com.cc4c.moderation;

import com.cc4c.community.api.BlogModerationUseCase;
import com.cc4c.community.api.BlogSummary;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/blogs")
public class ModerationController {
    private final BlogModerationUseCase useCase;

    ModerationController(BlogModerationUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/examine")
    public ApiResponse<PageResponse<BlogSummary>> pending(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(useCase.findPending(new PageQuery(page, size))));
    }

    @PutMapping("/approve/{id}")
    public ApiResponse<BlogSummary> approve(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.approve(id));
    }

    @PutMapping("/deny/{id}")
    public ApiResponse<BlogSummary> deny(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.deny(id));
    }
}
