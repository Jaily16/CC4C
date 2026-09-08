package com.cc4c.community.api;

import java.util.Date;

/**
 * 以不可变结构承载博客社区计算或查询结果。
 *
 * @param blogId 目标对象的稳定标识
 * @param writerId 目标对象的稳定标识
 * @param title 当前博客或课程的标题
 * @param publishTime 当前操作使用的时间点
 * @param click 调用方提供的 {@code click} 值
 * @param state 调用方提供的 {@code state} 值
 */
public record BlogSummary(
        String blogId, String writerId, String title, Date publishTime, Integer click, Integer state) {}
