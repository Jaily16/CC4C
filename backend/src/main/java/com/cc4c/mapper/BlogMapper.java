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

/**
 * 定义博客社区的 MyBatis 持久化及结果映射边界。
 */
@Mapper
public interface BlogMapper extends BaseMapper<BlogEntity> {

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param page 从零或接口约定起算的页码
     * @param languageId 目标对象的稳定标识
     * @return 当前操作产生的 IPage<BlogEntity> 结果
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param page 从零或接口约定起算的页码
     * @param userId 目标对象的稳定标识
     * @return 当前操作产生的 IPage<BlogEntity> 结果
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前方法约定返回结果集合
     */
    @Select("SELECT language_id FROM blog_involves_language WHERE blog_id = #{blogId} ORDER BY language_id")
    List<Integer> selectLanguageIds(long blogId);

    /**
     * 创建所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param blogId 目标对象的稳定标识
     * @param languageId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
    @Insert("INSERT INTO blog_involves_language(blog_id, language_id) VALUES(#{blogId}, #{languageId})")
    int insertLanguage(@Param("blogId") long blogId, @Param("languageId") int languageId);

    /**
     * 创建所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param userId 目标对象的稳定标识
     * @param blogId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
    @Insert(
            """
            INSERT INTO user_submits_blog(user_id, blog_id, submit_time)
            VALUES(#{userId}, #{blogId}, CURRENT_TIMESTAMP)
            """)
    int insertSubmission(@Param("userId") long userId, @Param("blogId") long blogId);

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param userId 目标对象的稳定标识
     * @param content 当前业务对象的正文内容
     * @return 按当前规则计算或读取的数值
     */
    @Insert(
            """
            INSERT INTO blog_draft(user_id, content)
            VALUES(#{userId}, #{content})
            ON DUPLICATE KEY UPDATE content = VALUES(content)
            """)
    int upsertDraft(@Param("userId") long userId, @Param("content") String content);

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param userId 目标对象的稳定标识
     * @return 按当前协议生成或读取的字符串值
     */
    @Select("SELECT content FROM blog_draft WHERE user_id = #{userId}")
    String selectDraft(long userId);

    /**
     * 删除或失效所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param userId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
    @Delete("DELETE FROM blog_draft WHERE user_id = #{userId}")
    int deleteDraft(long userId);

    /**
     * 记录所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param blogId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
    @Update("UPDATE blog SET click = click + 1 WHERE blog_id = #{blogId} AND deleted = 0")
    int incrementClick(long blogId);
}
