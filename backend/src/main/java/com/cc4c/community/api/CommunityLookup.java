package com.cc4c.community.api;

import java.util.Optional;

/**
 * 定义博客社区跨模块调用所需的稳定能力边界。
 */
public interface CommunityLookup {
    /**
     * 读取当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param blogId 目标对象的稳定标识
     * @return 存在时返回目标值，否则返回空的 Optional
     */
    Optional<BlogSnapshot> findBlog(long blogId);
}
