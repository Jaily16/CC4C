package com.cc4c.controller;

import com.cc4c.common.BusinessCode;
import com.cc4c.dto.ApiResponse;
import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.InteractionDtos.BlogCommentRequest;
import com.cc4c.dto.InteractionDtos.CommentResponse;
import com.cc4c.dto.InteractionDtos.CourseCommentRequest;
import com.cc4c.dto.InteractionDtos.CourseFavoriteSummary;
import com.cc4c.dto.InteractionDtos.ReplyCommentRequest;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResponse;
import com.cc4c.service.InteractionService;
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

/** 提供课程与博客收藏、评论读取及用户评论写入入口。 */
@Validated
@RestController
public class InteractionController {
    private final InteractionService service;

    /**
     * 接入当前用户的收藏、评论和评论查询服务。
     *
     * @param service 收藏与评论服务
     */
    InteractionController(InteractionService service) {
        this.service = service;
    }

    /**
     * 为当前用户收藏指定课程，成功使用 HTTP 201。
     *
     * @param courseId 正数课程 ID
     * @return 课程收藏结果
     */
    @PostMapping("/courses/star/{courseId}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteCourse(@PathVariable @Positive int courseId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_FAVOR_SUCCESS.code(), service.favoriteCourse(courseId), "课程收藏成功"));
    }

    /**
     * 查询当前用户是否已收藏指定课程。
     *
     * @param courseId 正数课程 ID
     * @return 课程收藏状态
     */
    @GetMapping("/courses/star/{courseId}")
    public ApiResponse<Boolean> isCourseFavorite(@PathVariable @Positive int courseId) {
        return ApiResponse.success(service.isCourseFavorite(courseId));
    }

    /**
     * 移除当前用户对指定课程的收藏。
     *
     * @param courseId 正数课程 ID
     * @return 取消课程收藏结果
     */
    @DeleteMapping("/courses/star/{courseId}")
    public ApiResponse<Boolean> removeCourseFavorite(@PathVariable @Positive int courseId) {
        return ApiResponse.success(
                BusinessCode.COURSE_DELETE_FAVOR_SUCCESS.code(), service.removeCourseFavorite(courseId), "课程取消收藏成功");
    }

    /**
     * 分页查询当前用户的课程收藏列表。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 课程收藏分页响应
     */
    @GetMapping("/courses/star")
    public ApiResponse<PageResponse<CourseFavoriteSummary>> courseFavorites(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(
                BusinessCode.COURSE_GET_FAVOR_COURSE_LIST_SUCCESS.code(),
                PageResponse.from(service.courseFavorites(new PageQuery(page, size))),
                null);
    }

    /**
     * 为当前用户收藏指定博客，成功使用 HTTP 201。
     *
     * @param blogId 正数博客 ID
     * @return 博客收藏结果
     */
    @PostMapping("/blogs/collect/{blogId}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteBlog(@PathVariable @Positive long blogId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.favoriteBlog(blogId)));
    }

    /**
     * 移除当前用户对指定博客的收藏。
     *
     * @param blogId 正数博客 ID
     * @return 取消博客收藏结果
     */
    @DeleteMapping("/blogs/collect/{blogId}")
    public ApiResponse<Boolean> removeBlogFavorite(@PathVariable @Positive long blogId) {
        return ApiResponse.success(service.removeBlogFavorite(blogId));
    }

    /**
     * 分页查询当前用户的博客收藏摘要。
     *
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 博客收藏分页响应
     */
    @GetMapping("/blogs/collect")
    public ApiResponse<PageResponse<BlogSummary>> blogFavorites(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.blogFavorites(new PageQuery(page, size))));
    }

    /**
     * 查询当前用户是否已收藏指定博客。
     *
     * @param blogId 正数博客 ID
     * @return 博客收藏状态
     */
    @GetMapping("/blogs/collect/{blogId}")
    public ApiResponse<Boolean> isBlogFavorite(@PathVariable @Positive long blogId) {
        return ApiResponse.success(service.isBlogFavorite(blogId));
    }

    /**
     * 将当前用户的课程评论交给交互服务保存，并返回 HTTP 201。
     *
     * @param request 已校验的课程 ID 和评论正文
     * @return 新课程评论响应
     */
    @PostMapping("/comments/course")
    public ResponseEntity<ApiResponse<CommentResponse>> commentCourse(
            @Valid @RequestBody CourseCommentRequest request) {
        return created(service.commentCourse(request));
    }

    /**
     * 将当前用户的博客评论交给交互服务保存，并返回 HTTP 201。
     *
     * @param request 已校验的博客 ID 和评论正文
     * @return 新博客评论响应
     */
    @PostMapping("/comments/blog")
    public ResponseEntity<ApiResponse<CommentResponse>> commentBlog(@Valid @RequestBody BlogCommentRequest request) {
        return created(service.commentBlog(request));
    }

    /**
     * 将针对父评论的回复交给交互服务保存，并返回 HTTP 201。
     *
     * @param request 已校验的父评论 ID 和回复正文
     * @return 新回复评论响应
     */
    @PostMapping("/comments/indirect")
    public ResponseEntity<ApiResponse<CommentResponse>> reply(@Valid @RequestBody ReplyCommentRequest request) {
        return created(service.reply(request));
    }

    /**
     * 委托服务按当前用户权限删除指定评论。
     *
     * @param commentId 正数评论 ID
     * @return 评论删除结果
     */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Boolean> deleteComment(@PathVariable @Positive long commentId) {
        return ApiResponse.success(service.deleteComment(commentId));
    }

    /**
     * 按课程 ID 分页查询评论及其组装结果。
     *
     * @param id 正数课程 ID
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 课程评论分页响应
     */
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

    /**
     * 按博客 ID 分页查询评论及其组装结果。
     *
     * @param id 正数博客 ID
     * @param page 从 1 起算的页码，默认 1
     * @param size 每页记录数，范围 1 至 100，默认 20
     * @return 博客评论分页响应
     */
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

    /**
     * 将已创建的评论包装为 HTTP 201 和评论创建业务码。
     *
     * @param response 交互服务返回的新评论
     * @return 包含评论数据的创建响应
     */
    private ResponseEntity<ApiResponse<CommentResponse>> created(CommentResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BusinessCode.COMMENT_ADD_SUCCESS.code(), response, null));
    }
}
