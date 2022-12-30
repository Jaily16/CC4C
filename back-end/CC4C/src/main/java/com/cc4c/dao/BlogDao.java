package com.cc4c.dao;

import com.cc4c.entity.Blog;
import com.cc4c.entity.Course;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BlogDao extends MPJBaseMapper<Blog> {
    //table blog_draft
    @Insert("insert into blog_draft(user_id,content) values(#{userId},#{content})")
    public int addDraft(Long userId, String content);

    @Select("select content from blog_draft where user_id = #{userId}")
    public String getDraft(Long userId);

    @Delete("delete from blog_draft where user_id = #{userId}")
    public int deleteDraft(Long userId);

    @Select("select count(*) from user_collects_blog where user_id = #{userId} and blog_id = #{blogId}")
    public Boolean ifCollect(Long userId, Long blogId);

    @Select("select blog.* from blog,user_collects_blog where " +
            "user_collects_blog.user_id = #{userId} and user_collects_blog.blog_id = blog.blog_id")
    public List<Blog> getCollectBlogs(Long userId);

}
