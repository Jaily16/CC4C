package com.cc4c.community.api;

import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;

public interface BlogModerationUseCase {
    PageResult<BlogSummary> findPending(PageQuery page);

    BlogSummary approve(long blogId);

    BlogSummary deny(long blogId);
}
