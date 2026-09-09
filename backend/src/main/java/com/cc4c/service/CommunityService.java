package com.cc4c.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import com.cc4c.config.MessagingProperties;
import com.cc4c.dto.BlogSnapshot;
import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.CommunityDtos.BlogDraftRequest;
import com.cc4c.dto.CommunityDtos.BlogResponse;
import com.cc4c.dto.CommunityDtos.BlogSubmitRequest;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;
import com.cc4c.entity.BlogEntity;
import com.cc4c.mapper.BlogMapper;
import com.cc4c.security.AccountRole;
import com.cc4c.security.CurrentActor;
import com.cc4c.security.RedisRateLimiter;
import com.cc4c.support.cache.BusinessCache;
import com.cc4c.support.messaging.AsyncEventTypes;
import com.cc4c.support.messaging.BlogReviewedNotificationV1;
import com.cc4c.support.messaging.BlogSubmittedNotificationV1;
import com.cc4c.support.messaging.TransactionalOutbox;
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

/** 协调博客读取、作者写入和审核；公共查询使用短缓存，提交及审核通知写入事务 Outbox。 */
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
     * 接入博客持久化、身份与目录查询、用户限流、缓存及可靠消息，并创建响应转换器。
     *
     * @param mapper 博客、草稿及关联数据访问 Mapper
     * @param identityLookup 用户展示快照查询接口
     * @param identityNotificationLookup 用户内部通知邮箱查询接口
     * @param catalogLookup 语言课程存在性及热度缓存失效接口
     * @param currentActor 当前业务身份读取接口
     * @param rateLimiter 当前用户发布或评论的频率限制器
     * @param cache 支持分区及提交后失效的业务缓存
     * @param outbox 事务内追加加密通知的 Outbox 服务
     * @param messagingProperties 审核通知收件人配置
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
     * 缓存审核通过博客的首页分页，按点击数和 ID 倒序排列，省略正文与语言列表。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 热门博客页
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
     * 缓存审核通过博客的列表分页，按发布时间和 ID 倒序排列。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 最新博客页
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
     * 按语言与分页条件缓存已审核博客列表。
     *
     * @param languageId 语言 ID
     * @param query 从 1 起算的页码及页大小
     * @return 指定语言的博客页
     */
    public PageResult<BlogResponse> byLanguage(int languageId, PageQuery query) {
        return cachedPage(
                LANGUAGE_REGION,
                languageId + ":" + pageKey(query),
                () -> responseMapper.toResponsePage(
                        mapper.selectByLanguage(new Page<>(query.page(), query.size()), languageId), false));
    }

    /**
     * 要求当前 USER 仍存在，直接查询其全部未删除博客，不筛选审核状态。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 当前作者的博客页
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
     * 直接按标题模糊查询审核通过博客，不缓存任意检索词。
     *
     * @param text 博客标题模糊检索词
     * @param query 从 1 起算的页码及页大小
     * @return 按发布时间及 ID 倒序排列的搜索结果
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
     * 先读取公共详情缓存；未命中公共数据时仅允许管理员或博客作者读取非公开详情。
     *
     * @param blogId 博客 ID
     * @return 包含正文和语言 ID 的博客详情
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
     * 限流并检查语言不重复且存在；事务内创建待审核博客、关联及审核邮件事件，同时删除作者现有草稿。
     *
     * @param request 标题、正文及不重复语言列表
     * @return 已保存的待审核博客详情
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
     * 只允许当前 USER 删除自己博客的持久化记录，提交后失效公共博客缓存；不删除图片文件。
     *
     * @param blogId 博客 ID
     * @return 删除流程完成后为 true
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
     * 在事务内按当前 USER 新建或覆盖唯一草稿正文。
     *
     * @param request 待新建或覆盖的草稿正文
     * @return 草稿保存后为 true
     */
    @Transactional
    public boolean saveDraft(BlogDraftRequest request) {
        long userId = currentActor.requiredUserId();
        mapper.upsertDraft(userId, request.content());
        return true;
    }

    /**
     * 按当前 USER 读取草稿正文。
     *
     * @return 草稿正文，不存在时为空
     */
    public String draft() {
        return mapper.selectDraft(currentActor.requiredUserId());
    }

    /**
     * 在事务内删除当前 USER 的草稿，不要求必须存在记录。
     *
     * @return 删除调用完成后为 true
     */
    @Transactional
    public boolean deleteDraft() {
        mapper.deleteDraft(currentActor.requiredUserId());
        return true;
    }

    /**
     * 对未删除博客原子增加点击数；未更新任何记录时抛出 404，不主动失效公共缓存。
     *
     * @param blogId 博客 ID
     * @return 点击更新成功时为 true
     */
    @Transactional
    public boolean click(long blogId) {
        if (mapper.incrementClick(blogId) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return true;
    }

    /**
     * 读取未删除博客并投影作者、标题和审核状态，不在此处执行可见性筛选。
     *
     * @param blogId 博客 ID
     * @return 博客快照，不存在时为空 Optional
     */
    @Override
    public Optional<BlogSnapshot> findBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        return Optional.ofNullable(blog)
                .map(value ->
                        new BlogSnapshot(value.getBlogId(), value.getWriterId(), value.getTitle(), value.getState()));
    }

    /**
     * 分页查询状态为待审核的博客并返回无正文摘要。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 按发布时间和 ID 倒序排列的待审核页
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
     * 在事务内将待审核博客转为通过状态并追加通知。
     *
     * @param blogId 博客 ID
     * @return 通过审核的博客摘要
     */
    @Override
    @Transactional
    public BlogSummary approve(long blogId) {
        return moderate(blogId, VERIFIED);
    }

    /**
     * 在事务内将待审核博客转为拒绝状态并追加通知。
     *
     * @param blogId 博客 ID
     * @return 拒绝审核的博客摘要
     */
    @Override
    @Transactional
    public BlogSummary deny(long blogId) {
        return moderate(blogId, DENIED);
    }

    /**
     * 仅处理待审核博客，通过时更新发布时间；通知收件人缺失则记录永久失败事件，随后安排公共缓存失效。
     *
     * @param blogId 博客 ID
     * @param state 目标审核状态，通过为 1、拒绝为 -1
     * @return 已更新状态的博客摘要
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
     * 读取未删除博客，不存在时抛出 404。
     *
     * @param blogId 博客 ID
     * @return 存在的博客实体
     */
    private BlogEntity requiredBlog(long blogId) {
        BlogEntity blog = mapper.selectById(blogId);
        if (blog == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, BusinessCode.NOT_FOUND, "Blog does not exist");
        }
        return blog;
    }

    /**
     * 仅允许 ADMIN 或 ID 与作者一致的 USER 读取非公开博客。
     *
     * @param blog 待转换或校验的博客实体
     * @return 当前身份具有非公开读取权限时为 true
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
     * 以显式分页类型读取公共缓存，未命中时加载结果，基础有效期为 15 秒。
     *
     * @param region 缓存分区名称
     * @param key 分区内的逻辑键
     * @param loader 缓存未命中时读取数据库的加载器
     * @return 公共博客分页结果
     */
    private PageResult<BlogResponse> cachedPage(String region, String key, Supplier<PageResult<BlogResponse>> loader) {
        return cache.getOrLoad(region, key, BLOG_PAGE_TYPE, PUBLIC_TTL, NEGATIVE_TTL, () -> Optional.of(loader.get()))
                .orElseThrow();
    }

    /**
     * 组合页码和页大小生成分页缓存逻辑键。
     *
     * @param query 从 1 起算的页码及页大小
     * @return 以冒号分隔的分页键
     */
    private String pageKey(PageQuery query) {
        return query.page() + ":" + query.size();
    }

    /** 安排提交后失效首页、全部列表、语言列表和详情四个公共博客分区。 */
    private void invalidatePublicBlogs() {
        cache.invalidateAfterCommit(HOME_REGION, ALL_REGION, LANGUAGE_REGION, DETAIL_REGION);
    }
}
