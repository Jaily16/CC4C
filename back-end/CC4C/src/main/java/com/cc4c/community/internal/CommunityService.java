package com.cc4c.community.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.community.CommunityDtos.BlogDraftRequest;
import com.cc4c.community.CommunityDtos.BlogResponse;
import com.cc4c.community.CommunityDtos.BlogSubmitRequest;
import com.cc4c.community.api.BlogModerationUseCase;
import com.cc4c.community.api.BlogSnapshot;
import com.cc4c.community.api.BlogSummary;
import com.cc4c.community.api.CommunityLookup;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.ActorIdentity;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;
import com.cc4c.shared.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class CommunityService implements CommunityLookup, BlogModerationUseCase {
    private static final int DENIED = -1;
    private static final int PENDING = 0;
    private static final int VERIFIED = 1;

    private final BlogMapper mapper;
    private final IdentityLookup identityLookup;
    private final CatalogLookup catalogLookup;
    private final CurrentActor currentActor;
    private final RedisRateLimiter rateLimiter;

    CommunityService(
            BlogMapper mapper,
            IdentityLookup identityLookup,
            CatalogLookup catalogLookup,
            CurrentActor currentActor,
            RedisRateLimiter rateLimiter) {
        this.mapper = mapper;
        this.identityLookup = identityLookup;
        this.catalogLookup = catalogLookup;
        this.currentActor = currentActor;
        this.rateLimiter = rateLimiter;
    }

    public PageResult<BlogResponse> home(PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, VERIFIED)
                .orderByDesc(BlogEntity::getClick)
                .orderByDesc(BlogEntity::getBlogId);
        return toResponsePage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
    }

    public PageResult<BlogResponse> all(PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, VERIFIED)
                .orderByDesc(BlogEntity::getPublishTime)
                .orderByDesc(BlogEntity::getBlogId);
        return toResponsePage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
    }

    public PageResult<BlogResponse> byLanguage(int languageId, PageQuery query) {
        return toResponsePage(
                mapper.selectByLanguage(new Page<>(query.page(), query.size()), languageId), false);
    }

    public PageResult<BlogResponse> byCurrentWriter(PageQuery query) {
        long userId = currentActor.requiredUserId();
        if (identityLookup.findUser(userId).isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "User does not exist");
        }
        return toResponsePage(mapper.selectByWriter(new Page<>(query.page(), query.size()), userId), false);
    }

    public PageResult<BlogResponse> search(String text, PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, VERIFIED)
                .like(BlogEntity::getTitle, text)
                .orderByDesc(BlogEntity::getPublishTime)
                .orderByDesc(BlogEntity::getBlogId);
        return toResponsePage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
    }

    public BlogResponse detail(long blogId) {
        BlogEntity blog = requiredBlog(blogId);
        if (blog.getState() != VERIFIED && !canReadNonPublic(blog)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return toResponse(blog, true);
    }

    @Transactional
    public BlogResponse submit(BlogSubmitRequest request) {
        long writerId = currentActor.requiredUserId();
        rateLimiter.checkBlogPublish(writerId);
        Set<Integer> languages = new LinkedHashSet<>(request.languageList());
        if (languages.size() != request.languageList().size()
                || languages.stream().anyMatch(language -> !catalogLookup.languageExists(language))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY,
                    "Blog language does not exist");
        }

        BlogEntity blog = new BlogEntity();
        blog.setWriterId(writerId);
        blog.setTitle(request.title());
        blog.setContent(request.content());
        blog.setPublishTime(new Date());
        blog.setClick(0);
        blog.setState(PENDING);
        mapper.insert(blog);
        languages.forEach(language -> mapper.insertLanguage(blog.getBlogId(), language));
        mapper.insertSubmission(writerId, blog.getBlogId());
        mapper.deleteDraft(writerId);
        return toResponse(blog, true);
    }

    @Transactional
    public boolean delete(long blogId) {
        long userId = currentActor.requiredUserId();
        BlogEntity blog = requiredBlog(blogId);
        if (!blog.getWriterId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权删除该博客");
        }
        mapper.deleteById(blogId);
        return true;
    }

    @Transactional
    public boolean saveDraft(BlogDraftRequest request) {
        long userId = currentActor.requiredUserId();
        mapper.upsertDraft(userId, request.content());
        return true;
    }

    public String draft() {
        return mapper.selectDraft(currentActor.requiredUserId());
    }

    @Transactional
    public boolean deleteDraft() {
        mapper.deleteDraft(currentActor.requiredUserId());
        return true;
    }

    @Transactional
    public boolean click(long blogId) {
        if (mapper.incrementClick(blogId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return true;
    }

    @Override
    public Optional<BlogSnapshot> findBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        return Optional.ofNullable(blog)
                .map(value -> new BlogSnapshot(
                        value.getBlogId(), value.getWriterId(), value.getTitle(), value.getState()));
    }

    @Override
    public PageResult<BlogSummary> findPending(PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, PENDING)
                .orderByDesc(BlogEntity::getPublishTime)
                .orderByDesc(BlogEntity::getBlogId);
        IPage<BlogEntity> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toSummary).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    @Override
    @Transactional
    public BlogSummary approve(long blogId) {
        return moderate(blogId, VERIFIED);
    }

    @Override
    @Transactional
    public BlogSummary deny(long blogId) {
        return moderate(blogId, DENIED);
    }

    private BlogSummary moderate(long blogId, int state) {
        BlogEntity blog = requiredBlog(blogId);
        if (blog.getState() != PENDING) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY,
                    "Blog is not pending moderation");
        }
        blog.setState(state);
        if (state == VERIFIED) {
            blog.setPublishTime(new Date());
        }
        mapper.updateById(blog);
        return toSummary(blog);
    }

    private BlogEntity requiredBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        if (blog == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return blog;
    }

    private boolean canReadNonPublic(BlogEntity blog) {
        return currentActor.current()
                .map(actor -> actor.role() == AccountRole.ADMIN
                        || (actor.role() == AccountRole.USER
                        && Long.toString(blog.getWriterId()).equals(actor.id())))
                .orElse(false);
    }

    private PageResult<BlogResponse> toResponsePage(IPage<BlogEntity> page, boolean includeContent) {
        return new PageResult<>(
                page.getRecords().stream().map(blog -> toResponse(blog, includeContent)).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    private BlogResponse toResponse(BlogEntity blog, boolean includeContent) {
        return new BlogResponse(
                Long.toString(blog.getBlogId()),
                Long.toString(blog.getWriterId()),
                blog.getTitle(),
                includeContent ? blog.getContent() : null,
                blog.getPublishTime(),
                blog.getClick(),
                blog.getState(),
                includeContent ? mapper.selectLanguageIds(blog.getBlogId()) : null);
    }

    private BlogSummary toSummary(BlogEntity blog) {
        return new BlogSummary(
                Long.toString(blog.getBlogId()),
                Long.toString(blog.getWriterId()),
                blog.getTitle(),
                blog.getPublishTime(),
                blog.getClick(),
                blog.getState());
    }
}
