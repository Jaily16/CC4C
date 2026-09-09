package com.cc4c.dto;

import java.util.Date;

/**
 * 用于博客列表的摘要；博客和作者 ID 使用字符串表示，避免客户端整数精度损失。
 *
 * @param blogId 博客 ID
 * @param writerId 博客作者 ID
 * @param title 博客标题
 * @param publishTime 博客发布时间
 * @param click 博客点击次数
 * @param state 博客审核状态
 */
public record BlogSummary(
        String blogId, String writerId, String title, Date publishTime, Integer click, Integer state) {}
