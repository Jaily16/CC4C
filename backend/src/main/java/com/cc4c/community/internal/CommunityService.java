package com.cc4c.community.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.catalog.api.CatalogLookup;
import com.cc4c.community.CommunityDtos.BlogDraftRequest;
import com.cc4c.community.CommunityDtos.BlogResponse;
import com.cc4c.community.CommunityDtos.BlogSubmitRequest;
import com.cc4c.community.api.BlogModerationUseCase;
import com.cc4c.community.api.BlogReviewedNotificationV1;
import com.cc4c.community.api.BlogSnapshot;
import com.cc4c.community.api.BlogSubmittedNotificationV1;
import com.cc4c.community.api.BlogSummary;
import com.cc4c.community.api.CommunityLookup;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.CurrentActor;
import com.cc4c.identity.api.IdentityLookup;
import com.cc4c.identity.api.IdentityNotificationLookup;
import com.cc4c.shared.AsyncEventTypes;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import com.cc4c.shared.MessagingProperties;
import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;
import com.cc4c.shared.RedisRateLimiter;
import com.cc4c.shared.TransactionalOutbox;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CommunityService 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Service
public class CommunityService implements CommunityLookup, BlogModerationUseCase {
    private static final int DENIED = -1;
    private static final int PENDING = 0;
    private static final int VERIFIED = 1;
    private static final String HOME_REGION = "community:home";
    private static final String ALL_REGION = "community:all";
    private static final String LANGUAGE_REGION = "community:language";
    private static final String DETAIL_REGION = "community:detail";
    private static final Duration PUBLIC_TTL = Duration.ofSeconds(15);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);
    private static final TypeReference<PageResult<BlogResponse>> BLOG_PAGE_TYPE = new TypeReference<>() {};
    private static final TypeReference<BlogResponse> BLOG_TYPE = new TypeReference<>() {};

    private final BlogMapper mapper;
    private final IdentityLookup identityLookup;
    private final IdentityNotificationLookup identityNotificationLookup;
    private final CatalogLookup catalogLookup;
    private final CurrentActor currentActor;
    private final RedisRateLimiter rateLimiter;
    private final BusinessCache cache;
    private final TransactionalOutbox outbox;
    private final MessagingProperties messagingProperties;
    private final CommunityResponseMapper responseMapper;

    /**
     * 创建 CommunityService 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param mapper 调用方提供的 {@code mapper} 值
     * @param identityLookup 调用方提供的 {@code identityLookup} 值
     * @param identityNotificationLookup 调用方提供的 {@code identityNotificationLookup} 值
     * @param catalogLookup 调用方提供的 {@code catalogLookup} 值
     * @param currentActor 调用方提供的 {@code currentActor} 值
     * @param rateLimiter 调用方提供的 {@code rateLimiter} 值
     * @param cache 调用方提供的 {@code cache} 值
     * @param outbox 调用方提供的 {@code outbox} 值
     * @param messagingProperties 调用方提供的 {@code messagingProperties} 值
     */
    CommunityService(
            BlogMapper mapper,
            IdentityLookup identityLookup,
            IdentityNotificationLookup identityNotificationLookup,
            CatalogLookup catalogLookup,
            CurrentActor currentActor,
            RedisRateLimiter rateLimiter,
            BusinessCache cache,
            TransactionalOutbox outbox,
            MessagingProperties messagingProperties) {
        this.mapper = mapper;
        this.identityLookup = identityLookup;
        this.identityNotificationLookup = identityNotificationLookup;
        this.catalogLookup = catalogLookup;
        this.currentActor = currentActor;
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.outbox = outbox;
        this.messagingProperties = messagingProperties;
        this.responseMapper = new CommunityResponseMapper(mapper);
    }

    /**
     * 执行 CommunityService 中的 home 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogResponse> home(PageQuery query) {
        return cachedPage(HOME_REGION, pageKey(query), () -> {
            LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                    .eq(BlogEntity::getState, VERIFIED)
                    .orderByDesc(BlogEntity::getClick)
                    .orderByDesc(BlogEntity::getBlogId);
            return responseMapper.toResponsePage(
                    mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
        });
    }

    /**
     * 执行 CommunityService 中的 all 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogResponse> all(PageQuery query) {
        return cachedPage(ALL_REGION, pageKey(query), () -> {
            LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                    .eq(BlogEntity::getState, VERIFIED)
                    .orderByDesc(BlogEntity::getPublishTime)
                    .orderByDesc(BlogEntity::getBlogId);
            return responseMapper.toResponsePage(
                    mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
        });
    }

    /**
     * 执行 CommunityService 中的 byLanguage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param languageId 目标对象的稳定标识
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogResponse> byLanguage(int languageId, PageQuery query) {
        return cachedPage(
                LANGUAGE_REGION,
                languageId + ":" + pageKey(query),
                () -> responseMapper.toResponsePage(
                        mapper.selectByLanguage(new Page<>(query.page(), query.size()), languageId), false));
    }

    /**
     * 执行 CommunityService 中的 byCurrentWriter 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogResponse> byCurrentWriter(PageQuery query) {
        long userId = currentActor.requiredUserId();
        if (identityLookup.findUser(userId).isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "User does not exist");
        }
        return responseMapper.toResponsePage(
                mapper.selectByWriter(new Page<>(query.page(), query.size()), userId), false);
    }

    /**
     * 执行 CommunityService 中的 search 职责，并保持既有权限、事务与副作用边界。
     *
     * @param text 调用方提供的 {@code text} 值
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    public PageResult<BlogResponse> search(String text, PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, VERIFIED)
                .like(BlogEntity::getTitle, text)
                .orderByDesc(BlogEntity::getPublishTime)
                .orderByDesc(BlogEntity::getBlogId);
        return responseMapper.toResponsePage(mapper.selectPage(new Page<>(query.page(), query.size()), wrapper), false);
    }

    /**
     * 执行 CommunityService 中的 detail 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public BlogResponse detail(long blogId) {
        Optional<BlogResponse> publicDetail = cache.getOrLoad(
                DETAIL_REGION, Long.toString(blogId), BLOG_TYPE, PUBLIC_TTL, NEGATIVE_TTL, () -> Optional.ofNullable(
                                mapper.selectById(blogId))
                        .filter(blog -> blog.getState() == VERIFIED)
                        .map(blog -> responseMapper.toResponse(blog, true)));
        if (publicDetail.isPresent()) {
            return publicDetail.get();
        }
        if (currentActor.current().isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        BlogEntity blog = requiredBlog(blogId);
        if (blog.getState() != VERIFIED && !canReadNonPublic(blog)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return responseMapper.toResponse(blog, true);
    }

    /**
     * 执行 CommunityService 中的 submit 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Transactional
    public BlogResponse submit(BlogSubmitRequest request) {
        long writerId = currentActor.requiredUserId();
        rateLimiter.checkBlogPublish(writerId);
        Set<Integer> languages = new LinkedHashSet<>(request.languageList());
        if (languages.size() != request.languageList().size()
                || languages.stream().anyMatch(language -> !catalogLookup.languageExists(language))) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY, BusinessCode.UNPROCESSABLE_ENTITY, "Blog language does not exist");
        }

        Instant submittedAt = Instant.now();
        BlogEntity blog = new BlogEntity();
        blog.setWriterId(writerId);
        blog.setTitle(request.title());
        blog.setContent(request.content());
        blog.setPublishTime(Date.from(submittedAt));
        blog.setClick(0);
        blog.setState(PENDING);
        mapper.insert(blog);
        languages.forEach(language -> mapper.insertLanguage(blog.getBlogId(), language));
        mapper.insertSubmission(writerId, blog.getBlogId());
        mapper.deleteDraft(writerId);
        String blogId = Long.toString(blog.getBlogId());
        for (String recipient : messagingProperties.moderationRecipientList()) {
            outbox.append(
                    AsyncEventTypes.BLOG_SUBMITTED,
                    "blog",
                    blogId,
                    new BlogSubmittedNotificationV1(recipient, blogId, blog.getTitle(), submittedAt),
                    submittedAt,
                    null);
        }
        return responseMapper.toResponse(blog, true);
    }

    /**
     * 删除 CommunityService 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean delete(long blogId) {
        long userId = currentActor.requiredUserId();
        BlogEntity blog = requiredBlog(blogId);
        if (!blog.getWriterId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, BusinessCode.FORBIDDEN, "无权删除该博客");
        }
        mapper.deleteById(blogId);
        invalidatePublicBlogs();
        return true;
    }

    /**
     * 执行 CommunityService 中的 saveDraft 职责，并保持既有权限、事务与副作用边界。
     *
     * @param request 已经过声明式校验的接口请求体
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean saveDraft(BlogDraftRequest request) {
        long userId = currentActor.requiredUserId();
        mapper.upsertDraft(userId, request.content());
        return true;
    }

    /**
     * 执行 CommunityService 中的 draft 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String draft() {
        return mapper.selectDraft(currentActor.requiredUserId());
    }

    /**
     * 删除 CommunityService 指定状态，并维持既有权限、事务与缓存失效边界。
     *
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean deleteDraft() {
        mapper.deleteDraft(currentActor.requiredUserId());
        return true;
    }

    /**
     * 执行 CommunityService 中的 click 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前条件是否成立
     */
    @Transactional
    public boolean click(long blogId) {
        if (mapper.incrementClick(blogId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return true;
    }

    /**
     * 查询并返回 CommunityService 中与 findBlog 对应的数据，不改变业务状态。
     *
     * @param blogId 目标对象的稳定标识
     * @return 存在时包含目标值，否则为空
     */
    @Override
    public Optional<BlogSnapshot> findBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        return Optional.ofNullable(blog)
                .map(value ->
                        new BlogSnapshot(value.getBlogId(), value.getWriterId(), value.getTitle(), value.getState()));
    }

    /**
     * 查询并返回 CommunityService 中与 findPending 对应的数据，不改变业务状态。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    @Override
    public PageResult<BlogSummary> findPending(PageQuery query) {
        LambdaQueryWrapper<BlogEntity> wrapper = new LambdaQueryWrapper<BlogEntity>()
                .eq(BlogEntity::getState, PENDING)
                .orderByDesc(BlogEntity::getPublishTime)
                .orderByDesc(BlogEntity::getBlogId);
        IPage<BlogEntity> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(responseMapper::toSummary).toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }

    /**
     * 执行 CommunityService 中的 approve 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Override
    @Transactional
    public BlogSummary approve(long blogId) {
        return moderate(blogId, VERIFIED);
    }

    /**
     * 执行 CommunityService 中的 deny 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    @Override
    @Transactional
    public BlogSummary deny(long blogId) {
        return moderate(blogId, DENIED);
    }

    /**
     * 执行 CommunityService 中的 moderate 职责，并保持既有权限、事务与副作用边界。
     *
     * @param blogId 目标对象的稳定标识
     * @param state 调用方提供的 {@code state} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private BlogSummary moderate(long blogId, int state) {
        BlogEntity blog = requiredBlog(blogId);
        if (blog.getState() != PENDING) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    BusinessCode.UNPROCESSABLE_ENTITY,
                    "Blog is not pending moderation");
        }
        blog.setState(state);
        if (state == VERIFIED) {
            blog.setPublishTime(new Date());
        }
        mapper.updateById(blog);
        Instant reviewedAt = Instant.now();
        String recipient = identityNotificationLookup
                .findNotificationContact(blog.getWriterId())
                .map(contact -> contact.email())
                .orElse("");
        BlogReviewedNotificationV1 notification = new BlogReviewedNotificationV1(
                recipient,
                Long.toString(blog.getBlogId()),
                blog.getTitle(),
                state == VERIFIED
                        ? BlogReviewedNotificationV1.ReviewOutcome.APPROVED
                        : BlogReviewedNotificationV1.ReviewOutcome.DENIED,
                reviewedAt);
        if (recipient.isBlank()) {
            outbox.appendPermanentFailure(
                    AsyncEventTypes.BLOG_REVIEWED,
                    "blog",
                    Long.toString(blog.getBlogId()),
                    notification,
                    reviewedAt,
                    null,
                    "RECIPIENT_UNAVAILABLE");
        } else {
            outbox.append(
                    AsyncEventTypes.BLOG_REVIEWED,
                    "blog",
                    Long.toString(blog.getBlogId()),
                    notification,
                    reviewedAt,
                    null);
        }
        invalidatePublicBlogs();
        return responseMapper.toSummary(blog);
    }

    /**
     * 校验 CommunityService 中与 requiredBlog 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private BlogEntity requiredBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        if (blog == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return blog;
    }

    /**
     * 判断 CommunityService 中与 canReadNonPublic 对应的条件是否成立。
     *
     * @param blog 调用方提供的 {@code blog} 值
     * @return 当前条件是否成立
     */
    private boolean canReadNonPublic(BlogEntity blog) {
        return currentActor
                .current()
                .map(actor -> actor.role() == AccountRole.ADMIN
                        || (actor.role() == AccountRole.USER
                                && Long.toString(blog.getWriterId()).equals(actor.id())))
                .orElse(false);
    }

    /**
     * 执行 CommunityService 中的 cachedPage 职责，并保持既有权限、事务与副作用边界。
     *
     * @param region 调用方提供的 {@code region} 值
     * @param key 调用方提供的 {@code key} 值
     * @param loader 调用方提供的 {@code loader} 值
     * @return 符合当前条件且保持稳定顺序的结果集合
     */
    private PageResult<BlogResponse> cachedPage(String region, String key, Supplier<PageResult<BlogResponse>> loader) {
        return cache.getOrLoad(region, key, BLOG_PAGE_TYPE, PUBLIC_TTL, NEGATIVE_TTL, () -> Optional.of(loader.get()))
                .orElseThrow();
    }

    /**
     * 执行 CommunityService 中的 pageKey 职责，并保持既有权限、事务与副作用边界。
     *
     * @param query 调用方提供的 {@code query} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String pageKey(PageQuery query) {
        return query.page() + ":" + query.size();
    }

    /**
     * 执行 CommunityService 中的 invalidatePublicBlogs 职责，并保持既有权限、事务与副作用边界。
     */
    private void invalidatePublicBlogs() {
        cache.invalidateAfterCommit(HOME_REGION, ALL_REGION, LANGUAGE_REGION, DETAIL_REGION);
    }
}
