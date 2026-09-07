package com.cc4c.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * 集中声明博客社区接口请求与响应的数据结构，不承载业务流程。
 */
public final class CommunityDtos {
    /**
     * 创建 CommunityDtos 实例，不触发外部 I/O。
     */
    private CommunityDtos() {}

    /**
     * 承载博客社区接口的输入字段与声明式校验约束。
     *
     * @param title 当前博客或课程的标题
     * @param content 当前业务对象的正文内容
     * @param languageList 调用方提供的 {@code languageList} 值
     */
    public record BlogSubmitRequest(
            @NotBlank @Size(max = 75) String title,
            @NotBlank String content,
            @NotEmpty List<@Positive Integer> languageList) {}

    /**
     * 承载博客社区接口的输入字段与声明式校验约束。
     *
     * @param content 当前业务对象的正文内容
     */
    public record BlogDraftRequest(@NotBlank String content) {}

    /**
     * 承载博客社区接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param blogId 目标对象的稳定标识
     * @param writerId 目标对象的稳定标识
     * @param title 当前博客或课程的标题
     * @param content 当前业务对象的正文内容
     * @param publishTime 当前操作使用的时间点
     * @param click 调用方提供的 {@code click} 值
     * @param state 调用方提供的 {@code state} 值
     * @param languageList 调用方提供的 {@code languageList} 值
     */
    public record BlogResponse(
            String blogId,
            String writerId,
            String title,
            String content,
            Date publishTime,
            Integer click,
            Integer state,
            List<Integer> languageList) {}
}
