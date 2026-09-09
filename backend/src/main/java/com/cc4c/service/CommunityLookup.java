package com.cc4c.service;

import com.cc4c.dto.BlogSnapshot;
import java.util.Optional;

/** 向收藏和评论服务提供博客存在性、作者和审核状态快照。 */
public interface CommunityLookup {
    /**
     * 读取博客的标识、作者及审核状态。
     *
     * @param blogId 博客 ID
     * @return 博客快照，不存在时为空 Optional
     */
    Optional<BlogSnapshot> findBlog(long blogId);
}
