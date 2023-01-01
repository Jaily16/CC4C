package com.cc4c.service;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.entity.Comment;

public interface CommentService {
    /**
     * 用户评论操作,返回评论的状态
     * @param comment 评论
     * @param type 评论的类型
     * @return 评论是否成功的状态码
     */
    public Code comment(Comment comment, Integer type);

    /**
     * 获取课程评论操作，返回结果
     * @param courseId 课程id
     * @return 该课程的评论列表结果
     */
    public Result getCourseComments(Integer courseId);

    /**
     * 获取博客评论操作，返回结果
     * @param blogId 博客id
     * @return 该博客的评论列表结果
     */
    public Result getBlogComments(Long blogId);
}
