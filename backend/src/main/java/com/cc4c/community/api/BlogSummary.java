package com.cc4c.community.api;

import java.util.Date;

/** BlogSummary 定义模块之间稳定、可验证的公开契约。 */
public record BlogSummary(
        String blogId, String writerId, String title, Date publishTime, Integer click, Integer state) {}
