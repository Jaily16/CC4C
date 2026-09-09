package com.cc4c.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cc4c.dto.CourseModuleRow;
import com.cc4c.dto.ModuleCourseNameRow;
import com.cc4c.entity.CourseEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 访问课程、语言及课程模块关联；自定义查询承担收藏数聚合和推荐筛选。 */
@Mapper
public interface CatalogMapper extends BaseMapper<CourseEntity> {

    /**
     * 聚合未删除课程的收藏数，按收藏数降序、课程 ID 升序分页。
     *
     * @param page MyBatis-Plus 分页对象，携带页码和页大小
     * @return 附带收藏数的课程分页结果
     */
    @Select(
            """
            SELECT c.course_id, c.course_name, c.language_name, c.description, c.level, c.state, c.deleted,
                   COUNT(ufc.user_id) AS favors_num
            FROM course c
            LEFT JOIN user_favors_course ufc ON ufc.course_id = c.course_id
            WHERE c.deleted = 0
            GROUP BY c.course_id, c.course_name, c.language_name, c.description, c.level, c.state, c.deleted
            ORDER BY favors_num DESC, c.course_id ASC
            """)
    IPage<CourseEntity> selectHome(Page<CourseEntity> page);

    /**
     * 读取未删除语言的名称。
     *
     * @param languageId 语言 ID
     * @return 语言名称，不存在时为空
     */
    @Select("SELECT language_name FROM programming_language WHERE language_id = #{languageId} AND deleted = 0")
    String findLanguageName(int languageId);

    /**
     * 按模块序号升序查询指定语言的全部模块。
     *
     * @param languageId 语言 ID
     * @return 语言模块列表
     */
    @Select(
            """
            SELECT language_id, priority, module_name, level
            FROM course_module
            WHERE language_id = #{languageId}
            ORDER BY priority ASC
            """)
    List<CourseModuleRow> selectModules(int languageId);

    /**
     * 按语言和包含两端的级别范围筛选模块，按序号升序排列。
     *
     * @param languageId 语言 ID
     * @param minimum 级别下限，包含该值
     * @param maximum 级别上限，包含该值
     * @return 满足级别范围的模块列表
     */
    @Select(
            """
            SELECT language_id, priority, module_name, level
            FROM course_module
            WHERE language_id = #{languageId} AND level BETWEEN #{minimum} AND #{maximum}
            ORDER BY priority ASC
            """)
    List<CourseModuleRow> selectModulesForRecommendation(
            @Param("languageId") int languageId, @Param("minimum") int minimum, @Param("maximum") int maximum);

    /**
     * 联合模块课程关系读取未删除课程名称，按模块序号及课程 ID 升序排列。
     *
     * @param languageId 语言 ID
     * @return 模块序号与课程名称投影
     */
    @Select(
            """
            SELECT mc.priority, c.course_name
            FROM module_course mc
            JOIN course c ON c.course_id = mc.course_id
            WHERE mc.language_id = #{languageId} AND c.deleted = 0
            ORDER BY mc.priority ASC, c.course_id ASC
            """)
    List<ModuleCourseNameRow> selectCourseNamesByLanguage(int languageId);

    /**
     * 读取级别处于指定范围或等于 66 的未删除课程名称，保留模块归属。
     *
     * @param languageId 语言 ID
     * @param minimum 级别下限，包含该值
     * @param maximum 级别上限，包含该值
     * @return 推荐课程的模块序号与名称投影
     */
    @Select(
            """
            SELECT mc.priority, c.course_name
            FROM module_course mc
            JOIN course c ON c.course_id = mc.course_id
            WHERE mc.language_id = #{languageId}
              AND (c.level BETWEEN #{minimum} AND #{maximum} OR c.level = 66)
              AND c.deleted = 0
            ORDER BY mc.priority ASC, c.course_id ASC
            """)
    List<ModuleCourseNameRow> selectRecommendedCourseNamesByLanguage(
            @Param("languageId") int languageId, @Param("minimum") int minimum, @Param("maximum") int maximum);

    /**
     * 按语言 ID 和模块序号统计匹配模块。
     *
     * @param languageId 语言 ID
     * @param priority 语言下的模块序号
     * @return 至少存在一个匹配模块时为 true
     */
    @Select(
            """
            SELECT COUNT(*)
            FROM course_module
            WHERE language_id = #{languageId} AND priority = #{priority}
            """)
    boolean moduleExists(@Param("languageId") int languageId, @Param("priority") int priority);

    /**
     * 插入指定语言下的课程模块。
     *
     * @param languageId 语言 ID
     * @param priority 语言下的模块序号
     * @param moduleName 课程模块名称
     * @param level 模块级别编码
     * @return 插入影响行数
     */
    @Insert(
            """
            INSERT INTO course_module(language_id, priority, module_name, level)
            VALUES(#{languageId}, #{priority}, #{moduleName}, #{level})
            """)
    int insertModule(
            @Param("languageId") int languageId,
            @Param("priority") int priority,
            @Param("moduleName") String moduleName,
            @Param("level") int level);

    /**
     * 插入课程与语言模块的关联。
     *
     * @param languageId 语言 ID
     * @param priority 语言下的模块序号
     * @param courseId 课程 ID
     * @return 插入影响行数
     */
    @Insert(
            """
            INSERT INTO module_course(language_id, priority, course_id)
            VALUES(#{languageId}, #{priority}, #{courseId})
            """)
    int insertModuleCourse(
            @Param("languageId") int languageId, @Param("priority") int priority, @Param("courseId") int courseId);
}
