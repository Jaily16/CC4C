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

/**
 * InteractionController 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Validated
@RestController
public class InteractionController {
    private final InteractionService service;

    /**
     * 创建 InteractionController 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param service 由容器注入的 InteractionService 协作组件
     */
    InteractionController(InteractionService service) {
        this.service = service;
    }

    /**
     * 执行 InteractionController 中的 favoriteCourse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param courseId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/courses/star/{courseId}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteCourse(@PathVariable @Positive int courseId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        BusinessCode.COURSE_ADD_FAVOR_SUCCESS.code(), service.favoriteCourse(courseId), "课程收藏成功"));
    }

    /**
     * 判断 InteractionController 中与 isCourseFavorite 对应的条件是否成立。
     *
     * @param courseId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/courses/star/{courseId}")
    public ApiResponse<Boolean> isCourseFavorite(@PathVariable @Positive int courseId) {
        return ApiResponse.success(service.isCourseFavorite(courseId));
    }

    /**
     * 删除 InteractionController 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param courseId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @DeleteMapping("/courses/star/{courseId}")
    public ApiResponse<Boolean> removeCourseFavorite(@PathVariable @Positive int courseId) {
        return ApiResponse.success(
                BusinessCode.COURSE_DELETE_FAVOR_SUCCESS.code(), service.removeCourseFavorite(courseId), "课程取消收藏成功");
    }

    /**
     * 执行 InteractionController 中的 courseFavorites 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 InteractionController 中的 favoriteBlog 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/blogs/collect/{blogId}")
    public ResponseEntity<ApiResponse<Boolean>> favoriteBlog(@PathVariable @Positive long blogId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.favoriteBlog(blogId)));
    }

    /**
     * 删除 InteractionController 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @DeleteMapping("/blogs/collect/{blogId}")
    public ApiResponse<Boolean> removeBlogFavorite(@PathVariable @Positive long blogId) {
        return ApiResponse.success(service.removeBlogFavorite(blogId));
    }

    /**
     * 执行 InteractionController 中的 blogFavorites 职责，并保持既有权限、事务与副作用边界。
     *
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/blogs/collect")
    public ApiResponse<PageResponse<BlogSummary>> blogFavorites(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(PageResponse.from(service.blogFavorites(new PageQuery(page, size))));
    }

    /**
     * 判断 InteractionController 中与 isBlogFavorite 对应的条件是否成立。
     *
     * @param blogId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @GetMapping("/blogs/collect/{blogId}")
    public ApiResponse<Boolean> isBlogFavorite(@PathVariable @Positive long blogId) {
        return ApiResponse.success(service.isBlogFavorite(blogId));
    }

    /**
     * 执行 InteractionController 中的 commentCourse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/comments/course")
    public ResponseEntity<ApiResponse<CommentResponse>> commentCourse(
            @Valid @RequestBody CourseCommentRequest request) {
        return created(service.commentCourse(request));
    }

    /**
     * 执行 InteractionController 中的 commentBlog 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/comments/blog")
    public ResponseEntity<ApiResponse<CommentResponse>> commentBlog(@Valid @RequestBody BlogCommentRequest request) {
        return created(service.commentBlog(request));
    }

    /**
     * 执行 InteractionController 中的 reply 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @PostMapping("/comments/indirect")
    public ResponseEntity<ApiResponse<CommentResponse>> reply(@Valid @RequestBody ReplyCommentRequest request) {
        return created(service.reply(request));
    }

    /**
     * 删除 InteractionController 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param commentId 目标对象的稳定标识
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Boolean> deleteComment(@PathVariable @Positive long commentId) {
        return ApiResponse.success(service.deleteComment(commentId));
    }

    /**
     * 执行 InteractionController 中的 courseComments 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 执行 InteractionController 中的 blogComments 职责，并保持既有权限、事务与副作用边界。
     *
     * @param id 目标对象的稳定标识
     * @param page 分页查询边界值
     * @param size 分页查询边界值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
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
     * 变更 InteractionController 对应状态，并维持既有校验、事务及外部副作用边界。
     *
     * @param response 调用方提供的 {@code response} 值
     * @return 使用统一协议封装且不暴露内部异常的接口响应
     */
    private ResponseEntity<ApiResponse<CommentResponse>> created(CommentResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BusinessCode.COMMENT_ADD_SUCCESS.code(), response, null));
    }
}
