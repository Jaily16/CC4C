package com.cc4c.catalog.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义课程目录的 MyBatis 持久化及结果映射边界。
 */
@Mapper
interface CatalogMapper extends BaseMapper<CourseEntity> {

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param page 从零或接口约定起算的页码
     * @return 当前操作产生的 IPage<CourseEntity> 结果
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
     * 读取所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @return 按当前协议生成或读取的字符串值
     */
    @Select("SELECT language_name FROM programming_language WHERE language_id = #{languageId} AND deleted = 0")
    String findLanguageName(int languageId);

    /**
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @return 按当前方法约定返回结果集合
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @param minimum 调用方提供的 {@code minimum} 值
     * @param maximum 调用方提供的 {@code maximum} 值
     * @return 按当前方法约定返回结果集合
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @return 按当前方法约定返回结果集合
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @param minimum 调用方提供的 {@code minimum} 值
     * @param maximum 调用方提供的 {@code maximum} 值
     * @return 按当前方法约定返回结果集合
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
     * 执行所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Select(
            """
            SELECT COUNT(*)
            FROM course_module
            WHERE language_id = #{languageId} AND priority = #{priority}
            """)
    boolean moduleExists(@Param("languageId") int languageId, @Param("priority") int priority);

    /**
     * 创建所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     * @param moduleName 调用方提供的 {@code moduleName} 值
     * @param level 调用方提供的 {@code level} 值
     * @return 按当前规则计算或读取的数值
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
     * 创建所需持久化数据，保持现有 SQL、锁和状态语义。
     *
     * @param languageId 目标对象的稳定标识
     * @param priority 调用方提供的 {@code priority} 值
     * @param courseId 目标对象的稳定标识
     * @return 按当前规则计算或读取的数值
     */
    @Insert(
            """
            INSERT INTO module_course(language_id, priority, course_id)
            VALUES(#{languageId}, #{priority}, #{courseId})
            """)
    int insertModuleCourse(
            @Param("languageId") int languageId, @Param("priority") int priority, @Param("courseId") int courseId);
}
