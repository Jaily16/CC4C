package com.cc4c.community.api;

import java.util.Optional;

public interface CommunityLookup {
    Optional<BlogSnapshot> findBlog(long blogId);
}
