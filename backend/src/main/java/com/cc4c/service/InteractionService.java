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

/** 协调当前用户的收藏和评论写入，检查归属与目标状态，并批量组装评论查询结果。 */
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
     * 接入交互 Mapper、身份与课程博客查询、当前用户及限流，并创建评论组装器。
     *
     * @param mapper 收藏和评论数据访问 Mapper
     * @param identityLookup 用户展示快照查询接口
     * @param catalogLookup 语言课程存在性及热度缓存失效接口
     * @param communityLookup 博客存在性及审核状态查询接口
     * @param currentActor 当前业务身份读取接口
     * @param rateLimiter 当前用户发布或评论的频率限制器
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
     * 事务内验证用户和课程存在，拒绝重复收藏，插入后安排课程热度缓存失效。
     *
     * @param courseId 课程 ID
     * @return 收藏插入成功时为 true
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
     * 删除当前用户的课程收藏；关系不存在时抛出 404，成功后安排热度缓存失效。
     *
     * @param courseId 课程 ID
     * @return 收藏删除成功时为 true
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
     * 读取当前 USER 与指定课程的收藏关系。
     *
     * @param courseId 课程 ID
     * @return 已收藏时为 true
     */
    public boolean isCourseFavorite(int courseId) {
        return mapper.courseFavoriteExists(currentActor.requiredUserId(), courseId);
    }

    /**
     * 确认当前用户存在后分页查询收藏课程，并转换为课程摘要。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 当前用户的课程收藏页
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
     * 事务内确认用户和已发布博客存在，拒绝重复收藏并插入关系。
     *
     * @param blogId 博客 ID
     * @return 博客收藏成功时为 true
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
     * 删除当前用户的博客收藏，不存在关系时抛出 404。
     *
     * @param blogId 博客 ID
     * @return 收藏删除成功时为 true
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
     * 读取当前 USER 与指定博客的收藏关系。
     *
     * @param blogId 博客 ID
     * @return 已收藏时为 true
     */
    public boolean isBlogFavorite(long blogId) {
        return mapper.blogFavoriteExists(currentActor.requiredUserId(), blogId);
    }

    /**
     * 确认当前用户存在后分页查询已审核博客收藏，长整数 ID 转为字符串。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 当前用户的博客收藏页
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
     * 检查用户评论频率及课程存在性，在同一事务内插入评论和课程直接关联。
     *
     * @param request 课程 ID 及评论正文
     * @return 层级为 0 的新评论响应
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
     * 检查评论频率、用户及博客已发布状态，在同一事务内插入评论和博客直接关联。
     *
     * @param request 博客 ID 及评论正文
     * @return 层级为 0 的新评论响应
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
     * 检查评论频率、用户和父评论，限制最多两级回复；事务内插入回复并补充父作者昵称。
     *
     * @param request 父评论 ID 及回复正文
     * @return 带父评论信息的新回复响应
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
     * 只允许当前 USER 逻辑删除自己发表的评论，不递归删除其回复。
     *
     * @param commentId 评论 ID
     * @return 删除成功时为 true
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
     * 检查课程存在后分页读取直接评论并批量组装回复。
     *
     * @param courseId 课程 ID
     * @param query 从 1 起算的页码及页大小
     * @return 课程顶层评论及回复页
     */
    public PageResult<CommentResponse> courseComments(int courseId, PageQuery query) {
        if (!catalogLookup.courseExists(courseId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Course does not exist");
        }
        return commentAssembler.assemble(mapper.selectCourseComments(new Page<>(query.page(), query.size()), courseId));
    }

    /**
     * 只检查博客存在性，再分页读取直接评论并组装回复；此方法不检查博客审核状态。
     *
     * @param blogId 博客 ID
     * @param query 从 1 起算的页码及页大小
     * @return 博客顶层评论及回复页
     */
    public PageResult<CommentResponse> blogComments(long blogId, PageQuery query) {
        if (communityLookup.findBlog(blogId).isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return commentAssembler.assemble(mapper.selectBlogComments(new Page<>(query.page(), query.size()), blogId));
    }

    /**
     * 插入评论正文、作者和当前时间，初始点赞数为零。
     *
     * @param userId 用户 ID
     * @param content 评论正文
     * @return 带已分配主键的新评论实体
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
     * 查询用户快照，用户不存在时抛出 422。
     *
     * @param userId 用户 ID
     * @return 存在的用户展示快照
     */
    private UserSnapshot requireUser(long userId) {
        return identityLookup.findUser(userId).orElseThrow(() -> unprocessable("User does not exist"));
    }

    /**
     * 以指定可展示提示构造 422 业务异常。
     *
     * @param message 可向客户端展示的业务提示
     * @return 不可处理实体异常
     */
    private BusinessException unprocessable(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY, message);
    }
}
