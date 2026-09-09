package com.cc4c.service;

import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;

/** 向审核入口提供待审核查询、通过和拒绝操作；访问角色由调用入口约束。 */
public interface BlogModerationUseCase {
    /**
     * 分页读取待审核博客摘要。
     *
     * @param page 从 1 起算的页码及页大小
     * @return 待审核博客页
     */
    PageResult<BlogSummary> findPending(PageQuery page);

    /**
     * 通过待审核博客，并在业务事务内追加审核通知。
     *
     * @param blogId 博客 ID
     * @return 通过后的博客摘要
     */
    BlogSummary approve(long blogId);

    /**
     * 拒绝待审核博客，并在业务事务内追加审核通知。
     *
     * @param blogId 博客 ID
     * @return 拒绝后的博客摘要
     */
    BlogSummary deny(long blogId);
}
