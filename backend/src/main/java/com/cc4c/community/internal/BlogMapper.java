package com.cc4c.community.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface BlogMapper extends BaseMapper<BlogEntity> {

    @Select(
            """
            SELECT b.*
            FROM blog b
            JOIN blog_involves_language bil ON bil.blog_id = b.blog_id
            WHERE bil.language_id = #{languageId} AND b.state = 1 AND b.deleted = 0
            ORDER BY b.publish_time DESC, b.blog_id DESC
            """)
    IPage<BlogEntity> selectByLanguage(Page<BlogEntity> page, int languageId);

    @Select(
            """
            SELECT b.*
            FROM blog b
            JOIN user_submits_blog usb ON usb.blog_id = b.blog_id
            WHERE usb.user_id = #{userId} AND b.deleted = 0
            ORDER BY b.publish_time DESC, b.blog_id DESC
            """)
    IPage<BlogEntity> selectByWriter(Page<BlogEntity> page, long userId);

    @Select("SELECT language_id FROM blog_involves_language WHERE blog_id = #{blogId} ORDER BY language_id")
    List<Integer> selectLanguageIds(long blogId);

    @Insert("INSERT INTO blog_involves_language(blog_id, language_id) VALUES(#{blogId}, #{languageId})")
    int insertLanguage(@Param("blogId") long blogId, @Param("languageId") int languageId);

    @Insert(
            """
            INSERT INTO user_submits_blog(user_id, blog_id, submit_time)
            VALUES(#{userId}, #{blogId}, CURRENT_TIMESTAMP)
            """)
    int insertSubmission(@Param("userId") long userId, @Param("blogId") long blogId);

    @Insert(
            """
            INSERT INTO blog_draft(user_id, content)
            VALUES(#{userId}, #{content})
            ON DUPLICATE KEY UPDATE content = VALUES(content)
            """)
    int upsertDraft(@Param("userId") long userId, @Param("content") String content);

    @Select("SELECT content FROM blog_draft WHERE user_id = #{userId}")
    String selectDraft(long userId);

    @Delete("DELETE FROM blog_draft WHERE user_id = #{userId}")
    int deleteDraft(long userId);

    @Update("UPDATE blog SET click = click + 1 WHERE blog_id = #{blogId} AND deleted = 0")
    int incrementClick(long blogId);
}
