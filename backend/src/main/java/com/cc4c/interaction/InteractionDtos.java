package com.cc4c.interaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Date;
import java.util.List;

/** InteractionDtos 表示身份、业务或交互边界上的数据传输结构。 */
public final class InteractionDtos {
    private InteractionDtos() {}

    /** CourseCommentRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record CourseCommentRequest(@NotBlank String content, @NotNull @Positive Integer courseId) {}

    /** BlogCommentRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record BlogCommentRequest(@NotBlank String content, @NotNull @Positive Long blogId) {}

    /** ReplyCommentRequest 表示身份、业务或交互边界上的数据传输结构。 */
    public record ReplyCommentRequest(@NotBlank String content, @NotNull @Positive Long fatherId) {}

    /** CourseFavoriteSummary 表示身份、业务或交互边界上的数据传输结构。 */
    public record CourseFavoriteSummary(Integer courseId, String courseName, String languageName) {}

    /** CommentResponse 表示身份、业务或交互边界上的数据传输结构。 */
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
