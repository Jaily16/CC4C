package com.cc4c.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.dto.BlogFavoriteRow;
import com.cc4c.dto.BlogSnapshot;
import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.CourseFavoriteRow;
import com.cc4c.dto.InteractionDtos.BlogCommentRequest;
import com.cc4c.dto.InteractionDtos.CommentResponse;
import com.cc4c.dto.InteractionDtos.CourseCommentRequest;
import com.cc4c.dto.InteractionDtos.CourseFavoriteSummary;
import com.cc4c.dto.InteractionDtos.ReplyCommentRequest;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;
import com.cc4c.dto.UserSnapshot;
import com.cc4c.entity.CommentEntity;
import com.cc4c.mapper.InteractionMapper;
import com.cc4c.security.CurrentActor;
import com.cc4c.security.RedisRateLimiter;
import java.util.Date;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * InteractionService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
public class InteractionService {
    private final InteractionMapper mapper;
    private final IdentityLookup identityLookup;
    private final CatalogLookup catalogLookup;
    private final CommunityLookup communityLookup;
    private final CurrentActor currentActor;
    private final RedisRateLimiter rateLimiter;
    private final InteractionCommentAssembler commentAssembler;

    /**
     * 创建 InteractionService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param mapper 调用方提供的 {@code mapper} 值
     * @param identityLookup 调用方提供的 {@code identityLookup} 值
     * @param catalogLookup 调用方提供的 {@code catalogLookup} 值
     * @param communityLookup 调用方提供的 {@code communityLookup} 值
     * @param currentActor 调用方提供的 {@code currentActor} 值
     * @param rateLimiter 调用方提供的 {@code rateLimiter} 值
     */
    InteractionService(
            InteractionMapper mapper,
            IdentityLookup identityLookup,
            CatalogLookup catalogLookup,
            CommunityLookup communityLookup,
            CurrentActor currentActor,
            RedisRateLimiter rateLimiter) {
        this.mapper = mapper;
        this.identityLookup = identityLookup;
        this.catalogLookup = catalogLookup;
        this.communityLookup = communityLookup;
        this.currentActor = currentActor;
        this.rateLimiter = rateLimiter;
        this.commentAssembler = new InteractionCommentAssembler(mapper);
    }

    /**
     * 执行 InteractionService 中的 favoriteCourse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param courseId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean favoriteCourse(int courseId) {
        long userId = currentActor.requiredUserId();
        requireUser(userId);
        if (!catalogLookup.courseExists(courseId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Course does not exist");
        }
        if (mapper.courseFavoriteExists(userId, courseId)) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "课程已收藏");
        }
        mapper.insertCourseFavorite(userId, courseId);
        catalogLookup.invalidateCoursePopularity();
        return true;
    }

    /**
     * 删除 InteractionService 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param courseId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean removeCourseFavorite(int courseId) {
        long userId = currentActor.requiredUserId();
        if (mapper.deleteCourseFavorite(userId, courseId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "课程收藏不存在");
        }
        catalogLookup.invalidateCoursePopularity();
        return true;
    }

    /**
     * 判断 InteractionService 中与 isCourseFavorite 对应的条件是否成立。
     *
     * @param courseId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    public boolean isCourseFavorite(int courseId) {
        return mapper.courseFavoriteExists(currentActor.requiredUserId(), courseId);
    }

    /**
     * 执行 InteractionService 中的 courseFavorites 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CourseFavoriteSummary> courseFavorites(PageQuery query) {
        long userId = currentActor.requiredUserId();
        requireUser(userId);
        IPage<CourseFavoriteRow> page = mapper.selectCourseFavorites(new Page<>(query.page(), query.size()), userId);
        return new PageResult<>(
                page.getRecords().stream()
                        .map(row -> new CourseFavoriteSummary(
                                row.getCourseId(), row.getCourseName(), row.getLanguageName()))
                        .toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    /**
     * 执行 InteractionService 中的 favoriteBlog 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean favoriteBlog(long blogId) {
        long userId = currentActor.requiredUserId();
        requireUser(userId);
        BlogSnapshot blog = communityLookup
                .findBlog(blogId)
                .orElseThrow(() ->
                        new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist"));
        if (blog.state() != 1) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY, "您不能收藏尚未发布的博客");
        }
        if (mapper.blogFavoriteExists(userId, blogId)) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "博客已收藏");
        }
        mapper.insertBlogFavorite(userId, blogId);
        return true;
    }

    /**
     * 删除 InteractionService 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean removeBlogFavorite(long blogId) {
        long userId = currentActor.requiredUserId();
        if (mapper.deleteBlogFavorite(userId, blogId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "博客收藏不存在");
        }
        return true;
    }

    /**
     * 判断 InteractionService 中与 isBlogFavorite 对应的条件是否成立。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    public boolean isBlogFavorite(long blogId) {
        return mapper.blogFavoriteExists(currentActor.requiredUserId(), blogId);
    }

    /**
     * 执行 InteractionService 中的 blogFavorites 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogSummary> blogFavorites(PageQuery query) {
        long userId = currentActor.requiredUserId();
        requireUser(userId);
        IPage<BlogFavoriteRow> page = mapper.selectBlogFavorites(new Page<>(query.page(), query.size()), userId);
        return new PageResult<>(
                page.getRecords().stream()
                        .map(row -> new BlogSummary(
                                Long.toString(row.getBlogId()),
                                Long.toString(row.getWriterId()),
                                row.getTitle(),
                                row.getPublishTime(),
                                row.getClick(),
                                row.getState()))
                        .toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    /**
     * 执行 InteractionService 中的 commentCourse 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional
    public CommentResponse commentCourse(CourseCommentRequest request) {
        long userId = currentActor.requiredUserId();
        rateLimiter.checkComment(userId);
        UserSnapshot user = requireUser(userId);
        if (!catalogLookup.courseExists(request.courseId())) {
            throw unprocessable("Course does not exist");
        }
        CommentEntity comment = insertComment(userId, request.content());
        mapper.insertCourseComment(comment.getCommentId(), request.courseId());
        return commentAssembler.toCreatedResponse(comment, user, null, 0, null);
    }

    /**
     * 执行 InteractionService 中的 commentBlog 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional
    public CommentResponse commentBlog(BlogCommentRequest request) {
        long userId = currentActor.requiredUserId();
        rateLimiter.checkComment(userId);
        UserSnapshot user = requireUser(userId);
        BlogSnapshot blog =
                communityLookup.findBlog(request.blogId()).orElseThrow(() -> unprocessable("Blog does not exist"));
        if (blog.state() != 1) {
            throw unprocessable("Blog is not published");
        }
        CommentEntity comment = insertComment(userId, request.content());
        mapper.insertBlogComment(comment.getCommentId(), request.blogId());
        return commentAssembler.toCreatedResponse(comment, user, null, 0, null);
    }

    /**
     * 执行 InteractionService 中的 reply 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional
    public CommentResponse reply(ReplyCommentRequest request) {
        long userId = currentActor.requiredUserId();
        rateLimiter.checkComment(userId);
        UserSnapshot user = requireUser(userId);
        CommentEntity parent = mapper.selectById(request.fatherId());
        if (parent == null) {
            throw unprocessable("Parent comment does not exist");
        }
        Integer storedLayer = mapper.selectLayer(parent.getCommentId());
        int layer = (storedLayer == null ? 0 : storedLayer) + 1;
        if (layer > 2) {
            throw unprocessable("Comment nesting cannot exceed two reply levels");
        }
        CommentEntity comment = insertComment(userId, request.content());
        mapper.insertReply(comment.getCommentId(), request.fatherId(), layer);
        String fatherName = identityLookup
                .findUser(parent.getUserId())
                .map(UserSnapshot::name)
                .orElse(null);
        return commentAssembler.toCreatedResponse(comment, user, request.fatherId(), layer, fatherName);
    }

    /**
     * 删除 InteractionService 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param commentId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean deleteComment(long commentId) {
        long userId = currentActor.requiredUserId();
        CommentEntity comment = mapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权删除该评论");
        }
        mapper.deleteById(commentId);
        return true;
    }

    /**
     * 执行 InteractionService 中的 courseComments 职责，并保持既有权限、事务与副作用边界。
     *
     * @param courseId 目标对象的稳定标识
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CommentResponse> courseComments(int courseId, PageQuery query) {
        if (!catalogLookup.courseExists(courseId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Course does not exist");
        }
        return commentAssembler.assemble(mapper.selectCourseComments(new Page<>(query.page(), query.size()), courseId));
    }

    /**
     * 执行 InteractionService 中的 blogComments 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<CommentResponse> blogComments(long blogId, PageQuery query) {
        if (communityLookup.findBlog(blogId).isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return commentAssembler.assemble(mapper.selectBlogComments(new Page<>(query.page(), query.size()), blogId));
    }

    /**
     * 执行 InteractionService 中的 insertComment 职责，并保持既有权限、事务与副作用边界。
     *
     * @param userId 目标对象的稳定标识
     * @param content 调用方提供的 {@code content} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private CommentEntity insertComment(long userId, String content) {
        CommentEntity comment = new CommentEntity();
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setTime(new Date());
        comment.setLike(0);
        mapper.insert(comment);
        return comment;
    }

    /**
     * 校验 InteractionService 中与 requireUser 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param userId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private UserSnapshot requireUser(long userId) {
        return identityLookup.findUser(userId).orElseThrow(() -> unprocessable("User does not exist"));
    }

    /**
     * 执行 InteractionService 中的 unprocessable 职责，并保持既有权限、事务与副作用边界。
     *
     * @param message 待处理的消息及其属性
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private BusinessException unprocessable(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY, message);
    }
}
