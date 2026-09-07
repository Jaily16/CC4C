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

/**
 * ModerationController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Validated
@RestController
@RequestMapping("/blogs")
public class ModerationController {
    private final BlogModerationUseCase useCase;

    /**
     * 创建 ModerationController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param useCase 调用方提供的 {@code useCase} 值
     */
    ModerationController(BlogModerationUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 执行 ModerationController 中的 pending 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/examine")
    public ApiResponse<PageResponse<BlogSummary>> pending(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(useCase.findPending(new PageQuery(page, size))));
    }

    /**
     * 执行 ModerationController 中的 approve 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/approve/{id}")
    public ApiResponse<BlogSummary> approve(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.approve(id));
    }

    /**
     * 执行 ModerationController 中的 deny 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PutMapping("/deny/{id}")
    public ApiResponse<BlogSummary> deny(@PathVariable @Positive long id) {
        return ApiResponse.success(useCase.deny(id));
    }
}
