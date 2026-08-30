package com.cc4c.community.api;

import com.cc4c.shared.PageQuery;
import com.cc4c.shared.PageResult;

/** BlogModerationUseCase 定义模块之间稳定、可验证的公开契约。 */
public interface BlogModerationUseCase {
    PageResult<BlogSummary> findPending(PageQuery page);

    BlogSummary approve(long blogId);

    BlogSummary deny(long blogId);
}
