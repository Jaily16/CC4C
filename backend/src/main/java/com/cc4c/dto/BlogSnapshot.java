package com.cc4c.dto;

/**
 * 供跨服务查询使用的博客标识、作者及审核状态快照。
 *
 * @param blogId 博客 ID
 * @param writerId 博客作者 ID
 * @param title 博客标题
 * @param state 博客审核状态
 */
public record BlogSnapshot(long blogId, long writerId, String title, int state) {}
