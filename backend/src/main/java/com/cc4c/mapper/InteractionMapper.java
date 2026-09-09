package com.cc4c.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.dto.BlogFavoriteRow;
import com.cc4c.dto.CommentRow;
import com.cc4c.dto.CourseFavoriteRow;
import com.cc4c.entity.CommentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 访问课程、博客收藏关系及评论关联，联合用户资料生成评论投影。 */
@Mapper
public interface InteractionMapper extends BaseMapper<CommentEntity> {

    /**
     * 检查指定用户与课程的收藏关系是否存在。
     *
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return 存在收藏关系时为 true
     */
    @Select("SELECT COUNT(*) FROM user_favors_course WHERE user_id = #{userId} AND course_id = #{courseId}")
    boolean courseFavoriteExists(@Param("userId") long userId, @Param("courseId") int courseId);

    /**
     * 新增课程收藏关系并由数据库记录收藏时间。
     *
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return 插入影响行数
     */
    @Insert(
            "INSERT INTO user_favors_course(user_id, course_id, time) VALUES(#{userId}, #{courseId}, CURRENT_TIMESTAMP)")
    int insertCourseFavorite(@Param("userId") long userId, @Param("courseId") int courseId);

    /**
     * 物理删除指定用户与课程的收藏关系。
     *
     * @param userId 用户 ID
     * @param courseId 课程 ID
     * @return 删除影响行数
     */
    @Delete("DELETE FROM user_favors_course WHERE user_id = #{userId} AND course_id = #{courseId}")
    int deleteCourseFavorite(@Param("userId") long userId, @Param("courseId") int courseId);

    /**
     * 分页查询用户收藏的未删除课程，按收藏时间降序、课程 ID 升序排列。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param userId 用户 ID
     * @return 课程收藏分页投影
     */
    @Select(
            """
            SELECT c.course_id, c.course_name, c.language_name
            FROM user_favors_course ufc
            JOIN course c ON c.course_id = ufc.course_id
            WHERE ufc.user_id = #{userId} AND c.deleted = 0
            ORDER BY ufc.time DESC, c.course_id ASC
            """)
    IPage<CourseFavoriteRow> selectCourseFavorites(Page<CourseFavoriteRow> page, long userId);

    /**
     * 检查指定用户与博客的收藏关系是否存在。
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return 存在收藏关系时为 true
     */
    @Select("SELECT COUNT(*) FROM user_collects_blog WHERE user_id = #{userId} AND blog_id = #{blogId}")
    boolean blogFavoriteExists(@Param("userId") long userId, @Param("blogId") long blogId);

    /**
     * 新增博客收藏关系并由数据库记录收藏时间。
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return 插入影响行数
     */
    @Insert("INSERT INTO user_collects_blog(user_id, blog_id, time) VALUES(#{userId}, #{blogId}, CURRENT_TIMESTAMP)")
    int insertBlogFavorite(@Param("userId") long userId, @Param("blogId") long blogId);

    /**
     * 物理删除指定用户与博客的收藏关系。
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return 删除影响行数
     */
    @Delete("DELETE FROM user_collects_blog WHERE user_id = #{userId} AND blog_id = #{blogId}")
    int deleteBlogFavorite(@Param("userId") long userId, @Param("blogId") long blogId);

    /**
     * 分页查询用户收藏且未删除、审核通过的博客，按收藏时间及博客 ID 倒序排列。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param userId 用户 ID
     * @return 博客收藏分页投影
     */
    @Select(
            """
            SELECT b.blog_id, b.writer_id, b.title, b.publish_time, b.click, b.state
            FROM user_collects_blog ucb
            JOIN blog b ON b.blog_id = ucb.blog_id
            WHERE ucb.user_id = #{userId} AND b.deleted = 0 AND b.state = 1
            ORDER BY ucb.time DESC, b.blog_id DESC
            """)
    IPage<BlogFavoriteRow> selectBlogFavorites(Page<BlogFavoriteRow> page, long userId);

    /**
     * 将已创建评论关联为指定课程的直接评论。
     *
     * @param commentId 评论 ID
     * @param courseId 课程 ID
     * @return 关联插入影响行数
     */
    @Insert("INSERT INTO course_direct_comment(comment_id, course_id) VALUES(#{commentId}, #{courseId})")
    int insertCourseComment(@Param("commentId") long commentId, @Param("courseId") int courseId);

    /**
     * 将已创建评论关联为指定博客的直接评论。
     *
     * @param commentId 评论 ID
     * @param blogId 博客 ID
     * @return 关联插入影响行数
     */
    @Insert("INSERT INTO blog_direct_comment(comment_id, blog_id) VALUES(#{commentId}, #{blogId})")
    int insertBlogComment(@Param("commentId") long commentId, @Param("blogId") long blogId);

    /**
     * 记录评论与父评论的回复关系及层级。
     *
     * @param commentId 评论 ID
     * @param fatherId 父评论 ID
     * @param layer 回复评论层级
     * @return 关联插入影响行数
     */
    @Insert("INSERT INTO indirect_comment(comment_id, father_id, layer) VALUES(#{commentId}, #{fatherId}, #{layer})")
    int insertReply(@Param("commentId") long commentId, @Param("fatherId") long fatherId, @Param("layer") int layer);

    /**
     * 按评论 ID 读取回复关系中的层级。
     *
     * @param commentId 评论 ID
     * @return 回复层级，无间接评论关联时为空
     */
    @Select("SELECT layer FROM indirect_comment WHERE comment_id = #{commentId}")
    Integer selectLayer(long commentId);

    /**
     * 分页读取课程直接评论及作者资料，排除已删除评论和用户。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param courseId 课程 ID
     * @return 按时间和评论 ID 倒序排列的评论投影
     */
    @Select(
            """
            SELECT c.comment_id, c.user_id, c.content, c.time, c.\u0060like\u0060,
                   u.user_name, u.avatar
            FROM course_direct_comment cdc
            JOIN comment c ON c.comment_id = cdc.comment_id
            JOIN user u ON u.user_id = c.user_id
            WHERE cdc.course_id = #{courseId} AND c.deleted = 0 AND u.deleted = 0
            ORDER BY c.time DESC, c.comment_id DESC
            """)
    IPage<CommentRow> selectCourseComments(Page<CommentRow> page, int courseId);

    /**
     * 分页读取博客直接评论及作者资料，排除已删除评论和用户。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @param blogId 博客 ID
     * @return 按时间和评论 ID 倒序排列的评论投影
     */
    @Select(
            """
            SELECT c.comment_id, c.user_id, c.content, c.time, c.\u0060like\u0060,
                   u.user_name, u.avatar
            FROM blog_direct_comment bdc
            JOIN comment c ON c.comment_id = bdc.comment_id
            JOIN user u ON u.user_id = c.user_id
            WHERE bdc.blog_id = #{blogId} AND c.deleted = 0 AND u.deleted = 0
            ORDER BY c.time DESC, c.comment_id DESC
            """)
    IPage<CommentRow> selectBlogComments(Page<CommentRow> page, long blogId);

    /**
     * 批量读取指定父评论的直接回复及双方作者信息，排除已删除回复和回复作者。
     *
     * @param fatherIds 待查询的非空父评论 ID 列表
     * @return 按时间和评论 ID 升序排列的回复投影
     */
    @Select({
        "<script>",
        "SELECT c.comment_id, c.user_id, c.content, c.time, c.\u0060like\u0060,",
        "       ic.father_id, ic.layer, u.user_name, u.avatar, fu.user_name AS father_name",
        "FROM indirect_comment ic",
        "JOIN comment c ON c.comment_id = ic.comment_id",
        "JOIN user u ON u.user_id = c.user_id",
        "JOIN comment fc ON fc.comment_id = ic.father_id",
        "JOIN user fu ON fu.user_id = fc.user_id",
        "WHERE ic.father_id IN",
        "<foreach item='id' collection='fatherIds' open='(' separator=',' close=')'>#{id}</foreach>",
        "AND c.deleted = 0 AND u.deleted = 0",
        "ORDER BY c.time ASC, c.comment_id ASC",
        "</script>"
    })
    List<CommentRow> selectReplies(@Param("fatherIds") List<Long> fatherIds);
}
