package com.cc4c.community.api;

import java.util.Optional;

/** CommunityLookup 定义模块之间稳定、可验证的公开契约。 */
public interface CommunityLookup {
    Optional<BlogSnapshot> findBlog(long blogId);
}
