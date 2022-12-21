package com.cc4c.service;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.entity.Comment;

public interface CommentService {
    /**
     * 用户评论操作,返回评论的状态
     * @param comment
     * @param type
     * @return
     */
    public Code comment(Comment comment, Integer type);

    /**
     * 获取课程评论操作，返回结果
     * @param courseId
     * @return
     */
    public Result getCourseComments(Integer courseId);

    /**
     * 获取博客评论操作，返回结果
     * @param blogId
     * @return
     */
    public Result getBlogComments(Long blogId);
}
