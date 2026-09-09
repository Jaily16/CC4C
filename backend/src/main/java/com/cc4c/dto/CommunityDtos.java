package com.cc4c.dto;

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
    /** 仅作为嵌套 DTO 的命名容器，禁止外部实例化。 */
    private CommunityDtos() {}

    /**
     * 提交博客的标题、正文及语言标签；正文不能为空，语言列表至少包含一项。
     *
     * @param title 博客标题
     * @param content 正文内容
     * @param languageList 博客关联的语言 ID 列表
     */
    public record BlogSubmitRequest(
            @NotBlank @Size(max = 75) String title,
            @NotBlank String content,
            @NotEmpty List<@Positive Integer> languageList) {}

    /**
     * 保存当前用户博客草稿的正文请求；发布流程对草稿的处理由服务负责。
     *
     * @param content 正文内容
     */
    public record BlogDraftRequest(@NotBlank String content) {}

    /**
     * 博客详情数据，包含正文及语言标签；长整数 ID 以字符串返回。
     *
     * @param blogId 博客 ID
     * @param writerId 博客作者 ID
     * @param title 博客标题
     * @param content 正文内容
     * @param publishTime 博客发布时间
     * @param click 博客点击次数
     * @param state 博客审核状态
     * @param languageList 博客关联的语言 ID 列表
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
