/**
 * 实现博客审核与异步消息运维入口，并保持管理员授权边界。
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "community :: api"})
package com.cc4c.moderation;
