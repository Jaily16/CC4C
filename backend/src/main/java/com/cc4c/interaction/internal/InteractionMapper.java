package com.cc4c.interaction.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface InteractionMapper extends BaseMapper<CommentEntity> {

    @Select("SELECT COUNT(*) FROM user_favors_course WHERE user_id = #{userId} AND course_id = #{courseId}")
    boolean courseFavoriteExists(@Param("userId") long userId, @Param("courseId") int courseId);

    @Insert(
            "INSERT INTO user_favors_course(user_id, course_id, time) VALUES(#{userId}, #{courseId}, CURRENT_TIMESTAMP)")
    int insertCourseFavorite(@Param("userId") long userId, @Param("courseId") int courseId);

    @Delete("DELETE FROM user_favors_course WHERE user_id = #{userId} AND course_id = #{courseId}")
    int deleteCourseFavorite(@Param("userId") long userId, @Param("courseId") int courseId);

    @Select(
            """
            SELECT c.course_id, c.course_name, c.language_name
            FROM user_favors_course ufc
            JOIN course c ON c.course_id = ufc.course_id
            WHERE ufc.user_id = #{userId} AND c.deleted = 0
            ORDER BY ufc.time DESC, c.course_id ASC
            """)
    IPage<CourseFavoriteRow> selectCourseFavorites(Page<CourseFavoriteRow> page, long userId);

    @Select("SELECT COUNT(*) FROM user_collects_blog WHERE user_id = #{userId} AND blog_id = #{blogId}")
    boolean blogFavoriteExists(@Param("userId") long userId, @Param("blogId") long blogId);

    @Insert("INSERT INTO user_collects_blog(user_id, blog_id, time) VALUES(#{userId}, #{blogId}, CURRENT_TIMESTAMP)")
    int insertBlogFavorite(@Param("userId") long userId, @Param("blogId") long blogId);

    @Delete("DELETE FROM user_collects_blog WHERE user_id = #{userId} AND blog_id = #{blogId}")
    int deleteBlogFavorite(@Param("userId") long userId, @Param("blogId") long blogId);

    @Select(
            """
            SELECT b.blog_id, b.writer_id, b.title, b.publish_time, b.click, b.state
            FROM user_collects_blog ucb
            JOIN blog b ON b.blog_id = ucb.blog_id
            WHERE ucb.user_id = #{userId} AND b.deleted = 0 AND b.state = 1
            ORDER BY ucb.time DESC, b.blog_id DESC
            """)
    IPage<BlogFavoriteRow> selectBlogFavorites(Page<BlogFavoriteRow> page, long userId);

    @Insert("INSERT INTO course_direct_comment(comment_id, course_id) VALUES(#{commentId}, #{courseId})")
    int insertCourseComment(@Param("commentId") long commentId, @Param("courseId") int courseId);

    @Insert("INSERT INTO blog_direct_comment(comment_id, blog_id) VALUES(#{commentId}, #{blogId})")
    int insertBlogComment(@Param("commentId") long commentId, @Param("blogId") long blogId);

    @Insert("INSERT INTO indirect_comment(comment_id, father_id, layer) VALUES(#{commentId}, #{fatherId}, #{layer})")
    int insertReply(@Param("commentId") long commentId, @Param("fatherId") long fatherId, @Param("layer") int layer);

    @Select("SELECT layer FROM indirect_comment WHERE comment_id = #{commentId}")
    Integer selectLayer(long commentId);

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
