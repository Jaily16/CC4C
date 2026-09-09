package com.cc4c.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.entity.BlogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 访问博客及语言、作者提交关联和用户草稿；博客状态与归属校验由服务负责。 */
@Mapper
public interface BlogMapper extends BaseMapper<BlogEntity> {

    /**
     * 分页查询指定语言下已通过审核且未删除的博客，按发布时间和 ID 倒序排列。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param languageId 语言 ID
     * @return 该语言的已审核博客分页结果
     */
    @Select(
            """
            SELECT b.*
            FROM blog b
            JOIN blog_involves_language bil ON bil.blog_id = b.blog_id
            WHERE bil.language_id = #{languageId} AND b.state = 1 AND b.deleted = 0
            ORDER BY b.publish_time DESC, b.blog_id DESC
            """)
    IPage<BlogEntity> selectByLanguage(Page<BlogEntity> page, int languageId);

    /**
     * 通过提交关系分页查询指定作者未删除的博客，不筛选审核状态。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param userId 用户 ID
     * @return 作者博客分页结果
     */
    @Select(
            """
            SELECT b.*
            FROM blog b
            JOIN user_submits_blog usb ON usb.blog_id = b.blog_id
            WHERE usb.user_id = #{userId} AND b.deleted = 0
            ORDER BY b.publish_time DESC, b.blog_id DESC
            """)
    IPage<BlogEntity> selectByWriter(Page<BlogEntity> page, long userId);

    /**
     * 查询博客关联的语言 ID，按 ID 升序排列。
     *
     * @param blogId 博客 ID
     * @return 语言 ID 列表
     */
    @Select("SELECT language_id FROM blog_involves_language WHERE blog_id = #{blogId} ORDER BY language_id")
    List<Integer> selectLanguageIds(long blogId);

    /**
     * 插入一条博客与语言的关联。
     *
     * @param blogId 博客 ID
     * @param languageId 语言 ID
     * @return 插入影响行数
     */
    @Insert("INSERT INTO blog_involves_language(blog_id, language_id) VALUES(#{blogId}, #{languageId})")
    int insertLanguage(@Param("blogId") long blogId, @Param("languageId") int languageId);

    /**
     * 插入用户提交博客的关联，并由数据库记录当前提交时间。
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return 插入影响行数
     */
    @Insert(
            """
            INSERT INTO user_submits_blog(user_id, blog_id, submit_time)
            VALUES(#{userId}, #{blogId}, CURRENT_TIMESTAMP)
            """)
    int insertSubmission(@Param("userId") long userId, @Param("blogId") long blogId);

    /**
     * 按用户 ID 新建草稿；已有草稿时覆盖正文。
     *
     * @param userId 用户 ID
     * @param content 用户草稿正文
     * @return 数据库报告的插入或更新影响行数
     */
    @Insert(
            """
            INSERT INTO blog_draft(user_id, content)
            VALUES(#{userId}, #{content})
            ON DUPLICATE KEY UPDATE content = VALUES(content)
            """)
    int upsertDraft(@Param("userId") long userId, @Param("content") String content);

    /**
     * 按用户 ID 读取草稿正文。
     *
     * @param userId 用户 ID
     * @return 草稿正文，不存在时为空
     */
    @Select("SELECT content FROM blog_draft WHERE user_id = #{userId}")
    String selectDraft(long userId);

    /**
     * 物理删除指定用户的草稿记录。
     *
     * @param userId 用户 ID
     * @return 删除影响行数
     */
    @Delete("DELETE FROM blog_draft WHERE user_id = #{userId}")
    int deleteDraft(long userId);

    /**
     * 对未删除博客的点击数执行数据库原子加一，不在此处筛选审核状态。
     *
     * @param blogId 博客 ID
     * @return 更新影响行数
     */
    @Update("UPDATE blog SET click = click + 1 WHERE blog_id = #{blogId} AND deleted = 0")
    int incrementClick(long blogId);
}
