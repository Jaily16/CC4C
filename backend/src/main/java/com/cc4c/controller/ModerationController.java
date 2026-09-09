package com.cc4c.controller;

import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResponse;
import com.cc4c.service.BlogModerationUseCase;
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

/** 提供管理员待审核博客查询及通过、拒绝入口。 */
@Validated
@RestController
@RequestMapping("/blogs")
public class ModerationController {
    private final BlogModerationUseCase useCase;

    /**
     * 接入博客审核用例，由用例管理状态变化与通知。
     *
     * @param useCase 博客审核状态转换用例
     */
    ModerationController(BlogModerationUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 分页查询待审核博客摘要。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 待审核博客分页响应
     */
    @GetMapping("/examine")
    public ApiResponse<PageResponse<BlogSummary>> pending(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(useCase.findPending(new PageQuery(page, size))));
    }

    /**
     * 委托审核用例通过指定博客。
     *
     * @param id 正数博客 ID
     * @return 审核后的博客摘要
     */
    @PutMapping("/approve/{id}")
    public ApiResponse<BlogSummary> approve(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.approve(id));
    }

    /**
     * 委托审核用例拒绝指定博客。
     *
     * @param id 正数博客 ID
     * @return 审核后的博客摘要
     */
    @PutMapping("/deny/{id}")
    public ApiResponse<BlogSummary> deny(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.deny(id));
    }
}
