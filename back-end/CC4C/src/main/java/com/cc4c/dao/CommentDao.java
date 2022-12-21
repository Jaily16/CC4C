package com.cc4c.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.Comment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentDao extends BaseMapper<Comment> {
    //table comment
    @Select("select comment.* from course_direct_comment,comment " +
            "where course_direct_comment.course_id = #{courseId} " +
            "and course_direct_comment.comment_id = comment.comment_id " +
            "order by comment.like desc")
    public List<Comment> getCourseComments(Integer courseId);

    @Select("select comment.* from blog_direct_comment,comment " +
            "where blog_direct_comment.blog_id = #{blogId} " +
            "and blog_direct_comment.comment_id = comment.comment_id " +
            "order by comment.like desc")
    public List<Comment> getBlogComments(Long blogId);

    @Select("select comment.*,father_id,layer from indirect_comment,comment " +
            "where indirect_comment.father_id = #{commentId} " +
            "and indirect_comment.comment_id = comment.comment_id")
    public List<Comment> getIndirectComments(Long commentId);

    @Select("select * from comment where comment_id = #{commentId} and deleted = 0")
    public Comment getById(Long commentId);


    //table course_direct_comment
    @Insert("insert into course_direct_comment(comment_id,course_id) values (#{commentId},#{courseId})")
    public int addCourseComment(Long commentId, Integer courseId);

    //table blog_direct_comment
    @Insert("insert into blog_direct_comment(comment_id,blog_id) values (#{commentId},#{blogId})")
    public int addBlogComment(Long commentId, Long blogId);

    //table indirect_comment
    @Insert("insert into indirect_comment(comment_id,father_id,layer) values (#{commentId},#{fatherId},#{layer})")
    public int addIndirectComment(Long commentId, Long fatherId, Integer layer);

}
