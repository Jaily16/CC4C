package com.cc4c.service;

import com.cc4c.dto.BlogSummary;
import com.cc4c.dto.PageQuery;
import com.cc4c.dto.PageResult;

/**
 * 定义博客社区跨模块调用所需的稳定能力边界。
 */
public interface BlogModerationUseCase {
    /**
     * 读取当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param page 从零或接口约定起算的页码
     * @return 包含分页元数据的查询结果
     */
    PageResult<BlogSummary> findPending(PageQuery page);

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前操作产生的 BlogSummary 结果
     */
    BlogSummary approve(long blogId);

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param blogId 目标对象的稳定标识
     * @return 当前操作产生的 BlogSummary 结果
     */
    BlogSummary deny(long blogId);
}
