package com.cc4c.interaction.internal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.community.api.BlogSnapshot;
import com.cc4c.community.api.BlogSummary;
import com.cc4c.community.api.CommunityLookup;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.UserSnapshot;
import com.cc4c.interaction.InteractionDtos.BlogCommentRequest;
import com.cc4c.interaction.InteractionDtos.CommentResponse;
import com.cc4c.interaction.InteractionDtos.CourseCommentRequest;
import com.cc4c.interaction.InteractionDtos.CourseFavoriteSummary;
import com.cc4c.interaction.InteractionDtos.ReplyCommentRequest;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InteractionService {
    private final InteractionMapper mapper;
    private final IdentityLookup identityLookup;
    private final CatalogLookup catalogLookup;
    private final CommunityLookup communityLookup;

    InteractionService(
            InteractionMapper mapper,
            IdentityLookup identityLookup,
            CatalogLookup catalogLookup,
            CommunityLookup communityLookup) {
        this.mapper = mapper;
        this.identityLookup = identityLookup;
        this.catalogLookup = catalogLookup;
        this.communityLookup = communityLookup;
    }

    @Transactional
    public boolean favoriteCourse(long userId, int courseId) {
        requireUser(userId);
        if (!catalogLookup.courseExists(courseId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Course does not exist");
        }
        if (mapper.courseFavoriteExists(userId, courseId)) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "课程已收藏");
        }
        mapper.insertCourseFavorite(userId, courseId);
        return true;
    }

    @Transactional
    public boolean removeCourseFavorite(long userId, int courseId) {
        if (mapper.deleteCourseFavorite(userId, courseId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "课程收藏不存在");
        }
        return true;
    }

    public boolean isCourseFavorite(long userId, int courseId) {
        return mapper.courseFavoriteExists(userId, courseId);
    }

    public PageResult<CourseFavoriteSummary> courseFavorites(long userId, PageQuery query) {
        requireUser(userId);
        IPage<CourseFavoriteRow> page = mapper.selectCourseFavorites(
                new Page<>(query.page(), query.size()), userId);
        return new PageResult<>(
                page.getRecords().stream()
                        .map(row -> new CourseFavoriteSummary(
                                row.getCourseId(), row.getCourseName(), row.getLanguageName()))
                        .toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    @Transactional
    public boolean favoriteBlog(long userId, long blogId) {
        requireUser(userId);
        BlogSnapshot blog = communityLookup.findBlog(blogId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist"));
        if (blog.state() != 1) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.UNPROCESSABLE_ENTITY, "您不能收藏尚未发布的博客");
        }
        if (mapper.blogFavoriteExists(userId, blogId)) {
            throw new BusinessException(HttpStatus.CONFLICT, BusinessCode.CONFLICT, "博客已收藏");
        }
        mapper.insertBlogFavorite(userId, blogId);
        return true;
    }

    @Transactional
    public boolean removeBlogFavorite(long userId, long blogId) {
        if (mapper.deleteBlogFavorite(userId, blogId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "博客收藏不存在");
        }
        return true;
    }

    public boolean isBlogFavorite(long userId, long blogId) {
        return mapper.blogFavoriteExists(userId, blogId);
    }

    public PageResult<BlogSummary> blogFavorites(long userId, PageQuery query) {
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

    @Transactional
    public CommentResponse commentCourse(CourseCommentRequest request) {
        UserSnapshot user = requireUser(request.userId());
        if (!catalogLookup.courseExists(request.courseId())) {
            throw unprocessable("Course does not exist");
        }
        CommentEntity comment = insertComment(request.userId(), request.content());
        mapper.insertCourseComment(comment.getCommentId(), request.courseId());
        return toCreatedResponse(comment, user, null, 0, null);
    }

    @Transactional
    public CommentResponse commentBlog(BlogCommentRequest request) {
        UserSnapshot user = requireUser(request.userId());
        BlogSnapshot blog = communityLookup.findBlog(request.blogId())
                .orElseThrow(() -> unprocessable("Blog does not exist"));
        if (blog.state() != 1) {
            throw unprocessable("Blog is not published");
        }
        CommentEntity comment = insertComment(request.userId(), request.content());
        mapper.insertBlogComment(comment.getCommentId(), request.blogId());
        return toCreatedResponse(comment, user, null, 0, null);
    }

    @Transactional
    public CommentResponse reply(ReplyCommentRequest request) {
        UserSnapshot user = requireUser(request.userId());
        CommentEntity parent = mapper.selectById(request.fatherId());
        if (parent == null) {
            throw unprocessable("Parent comment does not exist");
        }
        Integer storedLayer = mapper.selectLayer(parent.getCommentId());
        int layer = (storedLayer == null ? 0 : storedLayer) + 1;
        if (layer > 2) {
            throw unprocessable("Comment nesting cannot exceed two reply levels");
        }
        CommentEntity comment = insertComment(request.userId(), request.content());
        mapper.insertReply(comment.getCommentId(), request.fatherId(), layer);
        String fatherName = identityLookup.findUser(parent.getUserId())
                .map(UserSnapshot::name)
                .orElse(null);
        return toCreatedResponse(comment, user, request.fatherId(), layer, fatherName);
    }

    public PageResult<CommentResponse> courseComments(int courseId, PageQuery query) {
        if (!catalogLookup.courseExists(courseId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Course does not exist");
        }
        return comments(mapper.selectCourseComments(new Page<>(query.page(), query.size()), courseId));
    }

    public PageResult<CommentResponse> blogComments(long blogId, PageQuery query) {
        if (communityLookup.findBlog(blogId).isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return comments(mapper.selectBlogComments(new Page<>(query.page(), query.size()), blogId));
    }

    private PageResult<CommentResponse> comments(IPage<CommentRow> topLevelPage) {
        Map<Long, MutableComment> topLevel = new LinkedHashMap<>();
        topLevelPage.getRecords().forEach(row -> topLevel.put(row.getCommentId(), MutableComment.from(row)));

        if (!topLevel.isEmpty()) {
            List<CommentRow> firstRows = mapper.selectReplies(List.copyOf(topLevel.keySet()));
            Map<Long, MutableComment> firstLevel = new LinkedHashMap<>();
            firstRows.forEach(row -> {
                MutableComment reply = MutableComment.from(row);
                firstLevel.put(row.getCommentId(), reply);
                MutableComment parent = topLevel.get(row.getFatherId());
                if (parent != null) {
                    parent.replies.add(reply);
                }
            });
            if (!firstLevel.isEmpty()) {
                mapper.selectReplies(List.copyOf(firstLevel.keySet())).forEach(row -> {
                    MutableComment parent = firstLevel.get(row.getFatherId());
                    if (parent != null) {
                        parent.replies.add(MutableComment.from(row));
                    }
                });
            }
        }

        return new PageResult<>(
                topLevel.values().stream().map(MutableComment::toResponse).toList(),
                Math.toIntExact(topLevelPage.getCurrent()),
                Math.toIntExact(topLevelPage.getSize()),
                topLevelPage.getTotal());
    }

    private CommentEntity insertComment(long userId, String content) {
        CommentEntity comment = new CommentEntity();
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setTime(new Date());
        comment.setLike(0);
        mapper.insert(comment);
        return comment;
    }

    private UserSnapshot requireUser(long userId) {
        return identityLookup.findUser(userId)
                .orElseThrow(() -> unprocessable("User does not exist"));
    }

    private BusinessException unprocessable(String message) {
        return new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY, message);
    }

    private CommentResponse toCreatedResponse(
            CommentEntity comment, UserSnapshot user, Long fatherId, int layer, String fatherName) {
        return new CommentResponse(
                Long.toString(comment.getCommentId()),
                Long.toString(comment.getUserId()),
                comment.getContent(),
                comment.getTime(),
                comment.getLike(),
                fatherId == null ? null : Long.toString(fatherId),
                layer,
                user.name(),
                user.avatar(),
                fatherName,
                List.of());
    }

    private static final class MutableComment {
        private final CommentRow row;
        private final List<MutableComment> replies = new ArrayList<>();

        private MutableComment(CommentRow row) {
            this.row = row;
        }

        static MutableComment from(CommentRow row) {
            return new MutableComment(row);
        }

        CommentResponse toResponse() {
            return new CommentResponse(
                    Long.toString(row.getCommentId()),
                    Long.toString(row.getUserId()),
                    row.getContent(),
                    row.getTime(),
                    row.getLike(),
                    row.getFatherId() == null ? null : Long.toString(row.getFatherId()),
                    row.getLayer() == null ? 0 : row.getLayer(),
                    row.getUserName(),
                    row.getUserAvatar(),
                    row.getFatherName(),
                    replies.stream().map(MutableComment::toResponse).toList());
        }
    }
}
