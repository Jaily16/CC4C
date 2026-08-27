package com.cc4c.interaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Date;
import java.util.List;

public final class InteractionDtos {
    private InteractionDtos() {
    }

    public record CourseCommentRequest(
            @NotBlank String content,
            @NotNull @Positive Integer courseId
    ) {
    }

    public record BlogCommentRequest(
            @NotBlank String content,
            @NotNull @Positive Long blogId
    ) {
    }

    public record ReplyCommentRequest(
            @NotBlank String content,
            @NotNull @Positive Long fatherId
    ) {
    }

    public record CourseFavoriteSummary(Integer courseId, String courseName, String languageName) {
    }

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
            List<CommentResponse> subCommentList
    ) {
    }
}
