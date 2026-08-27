package com.cc4c.catalog.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
interface CatalogMapper extends BaseMapper<CourseEntity> {

    @Select("""
            SELECT c.course_id, c.course_name, c.language_name, c.description, c.level, c.state, c.deleted,
                   COUNT(ufc.user_id) AS favors_num
            FROM course c
            LEFT JOIN user_favors_course ufc ON ufc.course_id = c.course_id
            WHERE c.deleted = 0
            GROUP BY c.course_id, c.course_name, c.language_name, c.description, c.level, c.state, c.deleted
            ORDER BY favors_num DESC, c.course_id ASC
            """)
    IPage<CourseEntity> selectHome(Page<CourseEntity> page);

    @Select("SELECT language_name FROM programming_language WHERE language_id = #{languageId} AND deleted = 0")
    String findLanguageName(int languageId);

    @Select("""
            SELECT language_id, priority, module_name, level
            FROM course_module
            WHERE language_id = #{languageId}
            ORDER BY priority ASC
            """)
    List<CourseModuleRow> selectModules(int languageId);

    @Select("""
            SELECT language_id, priority, module_name, level
            FROM course_module
            WHERE language_id = #{languageId} AND level BETWEEN #{minimum} AND #{maximum}
            ORDER BY priority ASC
            """)
    List<CourseModuleRow> selectModulesForRecommendation(
            @Param("languageId") int languageId,
            @Param("minimum") int minimum,
            @Param("maximum") int maximum);

    @Select("""
            SELECT mc.priority, c.course_name
            FROM module_course mc
            JOIN course c ON c.course_id = mc.course_id
            WHERE mc.language_id = #{languageId} AND c.deleted = 0
            ORDER BY mc.priority ASC, c.course_id ASC
            """)
    List<ModuleCourseNameRow> selectCourseNamesByLanguage(int languageId);

    @Select("""
            SELECT mc.priority, c.course_name
            FROM module_course mc
            JOIN course c ON c.course_id = mc.course_id
            WHERE mc.language_id = #{languageId}
              AND (c.level BETWEEN #{minimum} AND #{maximum} OR c.level = 66)
              AND c.deleted = 0
            ORDER BY mc.priority ASC, c.course_id ASC
            """)
    List<ModuleCourseNameRow> selectRecommendedCourseNamesByLanguage(
            @Param("languageId") int languageId,
            @Param("minimum") int minimum,
            @Param("maximum") int maximum);

    @Select("""
            SELECT COUNT(*)
            FROM course_module
            WHERE language_id = #{languageId} AND priority = #{priority}
            """)
    boolean moduleExists(@Param("languageId") int languageId, @Param("priority") int priority);

    @Insert("""
            INSERT INTO course_module(language_id, priority, module_name, level)
            VALUES(#{languageId}, #{priority}, #{moduleName}, #{level})
            """)
    int insertModule(
            @Param("languageId") int languageId,
            @Param("priority") int priority,
            @Param("moduleName") String moduleName,
            @Param("level") int level);

    @Insert("""
            INSERT INTO module_course(language_id, priority, course_id)
            VALUES(#{languageId}, #{priority}, #{courseId})
            """)
    int insertModuleCourse(
            @Param("languageId") int languageId,
            @Param("priority") int priority,
            @Param("courseId") int courseId);
}
