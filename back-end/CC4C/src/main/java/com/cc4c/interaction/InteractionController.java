package com.cc4c.interaction;

import com.cc4c.community.api.BlogSummary;
import com.cc4c.interaction.InteractionDtos.BlogCommentRequest;
import com.cc4c.interaction.InteractionDtos.CommentResponse;
import com.cc4c.interaction.InteractionDtos.CourseCommentRequest;
import com.cc4c.interaction.InteractionDtos.CourseFavoriteSummary;
import com.cc4c.interaction.InteractionDtos.ReplyCommentRequest;
import com.cc4c.interaction.internal.InteractionService;
import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class InteractionController {
    private final InteractionService service;

    InteractionController(InteractionService service) {
        this.service = service;
    }

    @PostMapping("/courses/star/{userId}/{courseId}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteCourse(
            @PathVariable @Positive long userId, @PathVariable @Positive int courseId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_FAVOR_SUCCESS.code(),
                        service.favoriteCourse(userId, courseId),
                        "课程收藏成功"));
    }

    @GetMapping("/courses/ifFavor/{userId}/{courseId}")
    public ApiResponse<Boolean> isCourseFavorite(
            @PathVariable @Positive long userId, @PathVariable @Positive int courseId) {
        return ApiResponse.success(service.isCourseFavorite(userId, courseId));
    }

    @DeleteMapping("/courses/deleteFavor/{userId}/{courseId}")
    public ApiResponse<Boolean> removeCourseFavorite(
            @PathVariable @Positive long userId, @PathVariable @Positive int courseId) {
        return ApiResponse.success(
                BusinessCode.COURSE_DELETE_FAVOR_SUCCESS.code(),
                service.removeCourseFavorite(userId, courseId),
                "课程取消收藏成功");
    }

    @GetMapping("/courses/favorList/{id}")
    public ApiResponse<PageResponse<CourseFavoriteSummary>> courseFavorites(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_FAVOR_COURSE_LIST_SUCCESS.code(),
                PageResponse.from(service.courseFavorites(id, new PageQuery(page, size))),
                null);
    }

    @PostMapping("/blogs/collect/{uid}/{bid}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteBlog(
            @PathVariable @Positive long uid, @PathVariable @Positive long bid) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.favoriteBlog(uid, bid)));
    }

    @DeleteMapping("/blogs/collect/{uid}/{bid}")
    public ApiResponse<Boolean> removeBlogFavorite(
            @PathVariable @Positive long uid, @PathVariable @Positive long bid) {
        return ApiResponse.success(service.removeBlogFavorite(uid, bid));
    }

    @GetMapping("/blogs/collectList/{id}")
    public ApiResponse<PageResponse<BlogSummary>> blogFavorites(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.blogFavorites(id, new PageQuery(page, size))));
    }

    @GetMapping("/blogs/ifCollect/{uid}/{bid}")
    public ApiResponse<Boolean> isBlogFavorite(
            @PathVariable @Positive long uid, @PathVariable @Positive long bid) {
        return ApiResponse.success(service.isBlogFavorite(uid, bid));
    }

    @PostMapping("/comments/course")
    public ResponseEntity<ApiResponse<CommentResponse>> commentCourse(
            @Valid @RequestBody CourseCommentRequest request) {
        return created(service.commentCourse(request));
    }

    @PostMapping("/comments/blog")
    public ResponseEntity<ApiResponse<CommentResponse>> commentBlog(
            @Valid @RequestBody BlogCommentRequest request) {
        return created(service.commentBlog(request));
    }

    @PostMapping("/comments/indirect")
    public ResponseEntity<ApiResponse<CommentResponse>> reply(
            @Valid @RequestBody ReplyCommentRequest request) {
        return created(service.reply(request));
    }

    @GetMapping("/comments/course/{id}")
    public ApiResponse<PageResponse<CommentResponse>> courseComments(
            @PathVariable @Positive int id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COMMENT_GET_SUCCESS.code(),
                PageResponse.from(service.courseComments(id, new PageQuery(page, size))),
                null);
    }

    @GetMapping("/comments/blog/{id}")
    public ApiResponse<PageResponse<CommentResponse>> blogComments(
            @PathVariable @Positive long id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COMMENT_GET_SUCCESS.code(),
                PageResponse.from(service.blogComments(id, new PageQuery(page, size))),
                null);
    }

    private ResponseEntity<ApiResponse<CommentResponse>> created(CommentResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BusinessCode.COMMENT_ADD_SUCCESS.code(), response, null));
    }
}
