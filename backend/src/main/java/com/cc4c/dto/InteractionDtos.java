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
    /** 仅作为嵌套 DTO 的命名容器，禁止外部实例化。 */
    private InteractionDtos() {}

    /**
     * 在指定课程下新增非空正文评论的请求。
     *
     * @param content 正文内容
     * @param courseId 课程 ID
     */
    public record CourseCommentRequest(@NotBlank String content, @NotNull @Positive Integer courseId) {}

    /**
     * 在指定博客下新增非空正文评论的请求。
     *
     * @param content 正文内容
     * @param blogId 博客 ID
     */
    public record BlogCommentRequest(@NotBlank String content, @NotNull @Positive Long blogId) {}

    /**
     * 回复指定父评论的请求，父评论 ID 必须为正数。
     *
     * @param content 正文内容
     * @param fatherId 被回复的父评论 ID
     */
    public record ReplyCommentRequest(@NotBlank String content, @NotNull @Positive Long fatherId) {}

    /**
     * 用于收藏列表的课程标识、名称及语言名称。
     *
     * @param courseId 课程 ID
     * @param courseName 课程名称
     * @param languageName 语言名称
     */
    public record CourseFavoriteSummary(Integer courseId, String courseName, String languageName) {}

    /**
     * 评论树节点，包含作者、父评论作者和子评论；长整数 ID 以字符串返回。
     *
     * @param commentId 评论 ID
     * @param userId 评论作者 ID
     * @param content 正文内容
     * @param time 评论发表时间
     * @param like 评论点赞数
     * @param fatherId 被回复的父评论 ID
     * @param layer 评论层级
     * @param userName 评论作者昵称
     * @param userAvatar 评论作者头像
     * @param fatherName 父评论作者昵称
     * @param subCommentList 组装后的子评论列表
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
