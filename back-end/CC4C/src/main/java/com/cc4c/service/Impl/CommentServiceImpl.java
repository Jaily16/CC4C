package com.cc4c.service.Impl;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.dao.BlogDao;
import com.cc4c.dao.CommentDao;
import com.cc4c.dao.CourseDao;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.Comment;
import com.cc4c.entity.User;
import com.cc4c.service.CommentService;
import com.cc4c.service.UserService;
import com.cc4c.utility.CommentType;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private UserService userService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private CourseDao courseDao;
    @Autowired
    private BlogDao blogDao;

    @Override
    @Transactional
    public Code comment(Comment comment, Integer type) {
        if (comment == null || comment.getUserId() == null
                || userDao.selectById(comment.getUserId()) == null) {
            return Code.COMMENT_ADD_FAILED;
        }
        if (Objects.equals(type, CommentType.COURSE_COMMENT.getType())) {
            if (comment.getCourseId() == null || courseDao.selectById(comment.getCourseId()) == null) {
                return Code.COMMENT_ADD_COURSE_FAILED;
            }
        } else if (Objects.equals(type, CommentType.BLOG_COMMENT.getType())) {
            if (comment.getBlogId() == null || blogDao.selectById(comment.getBlogId()) == null) {
                return Code.COMMENT_ADD_BLOG_FAILED;
            }
        } else if (Objects.equals(type, CommentType.INDIRECT_COMMENT.getType())) {
            Comment parent = commentDao.getById(comment.getFatherId());
            if (parent == null) {
                return Code.COMMENT_ADD_INDIRECT_FAILED;
            }
            Integer parentLayer = commentDao.getIndirectLayer(parent.getCommentId());
            int layer = parentLayer == null ? 1 : parentLayer + 1;
            if (layer > 2) {
                return Code.COMMENT_ADD_INDIRECT_FAILED;
            }
            comment.setLayer(layer);
        } else {
            return Code.COMMENT_ADD_FAILED;
        }

        comment.setTime(new Date());
        if(commentDao.insert(comment) <= 0){
            return Code.COMMENT_ADD_FAILED;
        }
        if(Objects.equals(type, CommentType.COURSE_COMMENT.getType())){
            if(commentDao.addCourseComment(comment.getCommentId(), comment.getCourseId()) <= 0){
                throw new IllegalStateException("Unable to associate course comment");
            }
        }else if(Objects.equals(type, CommentType.BLOG_COMMENT.getType())){
            if(commentDao.addBlogComment(comment.getCommentId(), comment.getBlogId()) <= 0){
                throw new IllegalStateException("Unable to associate blog comment");
            }
        }else {

            if(commentDao.addIndirectComment(comment.getCommentId(), comment.getFatherId(), comment.getLayer()) <= 0){
                throw new IllegalStateException("Unable to associate indirect comment");
            }
        }
        return Code.COMMENT_ADD_SUCCESS;
    }

    @Override
    public Result getCourseComments(Integer courseId) {
        List<Comment> courseComments = commentDao.getCourseComments(courseId);
        if(!courseComments.isEmpty()){
            getUserInfo(courseComments);
            getIndirectComments(courseComments);
        }
        return new Result(Code.COMMENT_GET_SUCCESS.getCode(), courseComments);
    }

    @Override
    public Result getBlogComments(Long blogId) {
        List<Comment> blogComments = commentDao.getBlogComments(blogId);
        if(!blogComments.isEmpty()){
            getUserInfo(blogComments);
            getIndirectComments(blogComments);
        }
        return new Result(Code.COMMENT_GET_SUCCESS.getCode(), blogComments);
    }

    public void getIndirectComments(@NotNull List<Comment> commentList){
        for(Comment comment : commentList){
            List<Comment> indirectComments = commentDao.getIndirectComments(comment.getCommentId());
            getUserInfo(indirectComments);
            comment.setSubCommentList(indirectComments);
            for(Comment indirectComment : indirectComments){
                List<Comment> indirectComments1 = commentDao.getIndirectComments(indirectComment.getCommentId());
                getUserInfo(indirectComments1);
                indirectComment.setSubCommentList(indirectComments1);
            }
        }
    }

    public void getUserInfo(@NotNull List<Comment> commentList){
        for(Comment comment : commentList){
            User user = userService.getUserById(comment.getUserId());
            if (user != null) {
                comment.setUserName(user.getName());
                comment.setUserAvatar(user.getAvatar());
            }
            if(comment.getFatherId() != null){
                Comment fatherComment = commentDao.getById(comment.getFatherId());
                if (fatherComment != null) {
                    User father = userService.getUserById(fatherComment.getUserId());
                    if (father != null) {
                        comment.setFatherName(father.getName());
                    }
                }
            }
        }
    }
}
