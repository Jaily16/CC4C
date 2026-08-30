package com.cc4c.community.internal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cc4c.community.CommunityDtos.BlogResponse;
import com.cc4c.community.api.BlogSummary;
import com.cc4c.shared.PageResult;

/** 将博客实体转换为公开响应，避免服务协调层重复维护字段和分页规则。 */
/** CommunityResponseMapper 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class CommunityResponseMapper {
    private final BlogMapper mapper;

    CommunityResponseMapper(BlogMapper mapper) {
        this.mapper = mapper;
    }

    /** 将博客实体转换为博客响应，并按详情开关加载语言列表。 */
    public BlogResponse toResponse(BlogEntity blog, boolean includeContent) {
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

    /** 将博客实体转换为不含正文的摘要。 */
    public BlogSummary toSummary(BlogEntity blog) {
        return new BlogSummary(
                Long.toString(blog.getBlogId()),
                Long.toString(blog.getWriterId()),
                blog.getTitle(),
                blog.getPublishTime(),
                blog.getClick(),
                blog.getState());
    }

    /** 将 MyBatis 分页结果转换为 API 分页结果。 */
    public PageResult<BlogResponse> toResponsePage(IPage<BlogEntity> page, boolean includeContent) {
        return new PageResult<>(
                page.getRecords().stream()
                        .map(blog -> toResponse(blog, includeContent))
                        .toList(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }
}
