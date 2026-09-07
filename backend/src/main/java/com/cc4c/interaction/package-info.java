/**
 * 实现课程与博客收藏、评论和回复用例，校验当前访问者及目标资源。
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "identity :: api", "catalog :: api", "community :: api"})
package com.cc4c.interaction;
