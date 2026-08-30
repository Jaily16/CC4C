package com.cc4c.community.api;

/** BlogSnapshot 定义模块之间稳定、可验证的公开契约。 */
public record BlogSnapshot(long blogId, long writerId, String title, int state) {}
