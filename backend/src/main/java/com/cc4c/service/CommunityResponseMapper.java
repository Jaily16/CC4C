package com.cc4c.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.CommunityDtos.BlogResponse;
import com.cc4c.dto.PageResult;
import com.cc4c.entity.BlogEntity;
import com.cc4c.mapper.BlogMapper;

/** 将博客实体转换为响应，按详情开关控制正文与语言查询，并统一长整数 ID 和分页转换。 */
public final class CommunityResponseMapper {
    private final BlogMapper mapper;

    /**
     * 接入博客 Mapper，用于详情响应补充语言 ID。
     *
     * @param mapper 用于补充博客语言列表的 Mapper
     */
    CommunityResponseMapper(BlogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 将博客实体转换为博客响应，并按详情开关加载语言列表。
     *
     * @param blog 待转换或校验的博客实体
     * @param includeContent 是否包含正文并额外查询语言 ID
     * @return 详情包含正文及语言列表，列表模式将两者置空
     */
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

    /**
     * 将博客实体转换为不含正文的摘要。
     *
     * @param blog 待转换或校验的博客实体
     * @return ID 为字符串的博客摘要
     */
    public BlogSummary toSummary(BlogEntity blog) {
        return new BlogSummary(
                Long.toString(blog.getBlogId()),
                Long.toString(blog.getWriterId()),
                blog.getTitle(),
                blog.getPublishTime(),
                blog.getClick(),
                blog.getState());
    }

    /**
     * 将 MyBatis 分页结果转换为 API 分页结果。
     *
     * @param page MyBatis 分页结果，携带记录及分页元数据
     * @param includeContent 是否包含正文并额外查询语言 ID
     * @return 保留总数和页码的博客响应页
     */
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
