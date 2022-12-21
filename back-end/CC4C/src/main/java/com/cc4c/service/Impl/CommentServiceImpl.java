package com.cc4c.service.Impl;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.dao.CommentDao;
import com.cc4c.entity.Comment;
import com.cc4c.service.CommentService;
import com.cc4c.service.UserService;
import com.cc4c.utility.CommentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private UserService userService;
    @Override
    public Code comment(Comment comment, Integer type) {
        Date date = new Date();
        comment.setTime(date);
        if(commentDao.insert(comment) <= 0){
            return Code.COMMENT_ADD_FAILED;
        }
        if(Objects.equals(type, CommentType.COURSE_COMMENT.getType())){
            if(commentDao.addCourseComment(comment.getCommentId(), comment.getCourseId()) <= 0){
                return Code.COMMENT_ADD_COURSE_FAILED;
            }
        }else if(Objects.equals(type, CommentType.BLOG_COMMENT.getType())){
            if(commentDao.addBlogComment(comment.getCommentId(), comment.getBlogId()) <= 0){
                return Code.COMMENT_ADD_BLOG_FAILED;
            }
        }else {

            if(commentDao.addIndirectComment(comment.getCommentId(), comment.getFatherId(), comment.getLayer()) <= 0){
                return Code.COMMENT_ADD_INDIRECT_FAILED;
            }
        }
        return Code.COMMENT_ADD_SUCCESS;
    }

    @Override
    public Result getCourseComments(Integer courseId) {
        List<Comment> courseComments = commentDao.getCourseComments(courseId);
        if(!courseComments.isEmpty()){
            getUserName(courseComments);
            getIndirectComments(courseComments);
        }
        return new Result(Code.COMMENT_GET_SUCCESS.getCode(), courseComments);
    }

    @Override
    public Result getBlogComments(Long blogId) {
        List<Comment> blogComments = commentDao.getBlogComments(blogId);
        if(!blogComments.isEmpty()){
            getUserName(blogComments);
            getIndirectComments(blogComments);
        }
        return new Result(Code.COMMENT_GET_SUCCESS.getCode(), blogComments);
    }

    public void getIndirectComments(@NotNull List<Comment> commentList){
        for(Comment comment : commentList){
            List<Comment> indirectComments = commentDao.getIndirectComments(comment.getCommentId());
            getUserName(indirectComments);
            comment.setSubCommentList(indirectComments);
            for(Comment indirectComment : indirectComments){
                List<Comment> indirectComments1 = commentDao.getIndirectComments(indirectComment.getCommentId());
                getUserName(indirectComments1);
                indirectComment.setSubCommentList(indirectComments1);
            }
        }
    }

    public void getUserName(@NotNull List<Comment> commentList){
        for(Comment comment : commentList){
            comment.setUserName(userService.getUserNameById(comment.getUserId()));
            if(comment.getFatherId() != null){
                Comment fatherComment = commentDao.getById(comment.getFatherId());
                comment.setFatherName(userService.getUserNameById(fatherComment.getUserId()));
            }
        }
    }
}
