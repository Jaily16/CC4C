package com.cc4c.dto;

/**
 * 以不可变结构承载博客社区计算或查询结果。
 *
 * @param blogId 目标对象的稳定标识
 * @param writerId 目标对象的稳定标识
 * @param title 当前博客或课程的标题
 * @param state 调用方提供的 {@code state} 值
 */
public record BlogSnapshot(long blogId, long writerId, String title, int state) {}
