package com.cc4c.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Date;
import java.util.List;

/**
 * 集中声明收藏与评论接口请求与响应的数据结构，不承载业务流程。
 */
public final class InteractionDtos {
    /**
     * 创建 InteractionDtos 实例，不触发外部 I/O。
     */
    private InteractionDtos() {}

    /**
     * 承载收藏与评论接口的输入字段与声明式校验约束。
     *
     * @param content 当前业务对象的正文内容
     * @param courseId 目标对象的稳定标识
     */
    public record CourseCommentRequest(@NotBlank String content, @NotNull @Positive Integer courseId) {}

    /**
     * 承载收藏与评论接口的输入字段与声明式校验约束。
     *
     * @param content 当前业务对象的正文内容
     * @param blogId 目标对象的稳定标识
     */
    public record BlogCommentRequest(@NotBlank String content, @NotNull @Positive Long blogId) {}

    /**
     * 承载收藏与评论接口的输入字段与声明式校验约束。
     *
     * @param content 当前业务对象的正文内容
     * @param fatherId 目标对象的稳定标识
     */
    public record ReplyCommentRequest(@NotBlank String content, @NotNull @Positive Long fatherId) {}

    /**
     * 以不可变结构承载收藏与评论计算或查询结果。
     *
     * @param courseId 目标对象的稳定标识
     * @param courseName 调用方提供的 {@code courseName} 值
     * @param languageName 调用方提供的 {@code languageName} 值
     */
    public record CourseFavoriteSummary(Integer courseId, String courseName, String languageName) {}

    /**
     * 承载收藏与评论接口的脱敏响应字段，不暴露内部凭据或异常。
     *
     * @param commentId 目标对象的稳定标识
     * @param userId 目标对象的稳定标识
     * @param content 当前业务对象的正文内容
     * @param time 调用方提供的 {@code time} 值
     * @param like 调用方提供的 {@code like} 值
     * @param fatherId 目标对象的稳定标识
     * @param layer 调用方提供的 {@code layer} 值
     * @param userName 调用方提供的 {@code userName} 值
     * @param userAvatar 调用方提供的 {@code userAvatar} 值
     * @param fatherName 调用方提供的 {@code fatherName} 值
     * @param subCommentList 调用方提供的 {@code subCommentList} 值
     */
    public record CommentResponse(
            String commentId,
            String userId,
            String content,
            Date time,
            Integer like,
            String fatherId,
            Integer layer,
            String userName,
            String userAvatar,
            String fatherName,
            List<CommentResponse> subCommentList) {}
}
