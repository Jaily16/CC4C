package com.cc4c.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ModuleCourse;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

@Mapper
public interface CourseDao extends BaseMapper<Course> {
    //table course
    @Select("select course_name from course where course_id = #{courseId}")
    public String getCourseNameById(Integer courseId);

    @Select("select course.* from course,user_favors_course where " +
            "user_favors_course.user_id = #{userId} and user_favors_course.course_id = course.course_id")
    public List<Course> getFavorCourses(Long userId);

    //table course_video
//    @Insert("insert into course_video (course_name,video_platform,video_link) values (#{courseName},#{platform},#{url})")
//    public int addCourseVideo(String courseName, String platform, String url);
//
//    @Select("select video_platform, video_link from course_video where course_name = #{courseName}")
//    @Results({@Result(property = "platform", column = "video_platform"),
//              @Result(property = "url", column = "video_link")})
//    public List<CourseVideo> getCourseVideosByName(String courseName);

//    @Delete("delete from course_video where course_name = #{courseName}")
//    public int deleteCourseVideoByName(String courseName);

    //table course_module
    @Insert("insert into course_module (language_id,priority,module_name,level) values(#{languageId},#{priority},#{moduleName},#{level})")
    public int addCourseModule(CourseModule courseModule);
    @Select("select count(*) from course_module where language_id = #{languageId} and priority = #{priority}")
    public Boolean countCourseModule(CourseModule courseModule);
    @Select("select * from course_module where language_id = #{languageId}")
    public List<CourseModule> getCourseModulesByLId(Integer languageId);
    @Select("select * from course_module where language_id = #{languageId} " +
            "and level >= #{smallLevel} and level <= #{bigLevel} order by priority asc")
    public List<CourseModule> getCourseModuleByLIdAndRange(Integer languageId, Integer smallLevel, Integer bigLevel);
    @Select("select course_module.* from course_module,module_course,course where module_course.course_id = course.course_id " +
            "and course_name = #{courseName} and " +
            "module_course.language_id = course_module.language_id and module_course.priority = course_module.priority")
    public CourseModule getCourseModuleByCName(String courseName);
    @Delete("delete from course_module where language_id = #{languageId} and priority = #{priority}")
    public int deleteCourseModule(CourseModule courseModule);

    //table module_course
    @Insert("insert into module_course (language_id,priority,course_id) values(#{languageId},#{priority},#{courseId})")
    public int addModuleCourse(ModuleCourse moduleCourse);
    @Delete("delete from module_course where language_id = #{languageId} and priority = #{priority}")
    public int deleteModuleCourseByModule(Integer languageId, Integer priority);
    @Delete("delete from module_course where language_id = #{languageId} and priority = #{priority} and course_id = #{courseId}")
    public int deleteModuleCourse(ModuleCourse moduleCourse);
    @Select("select course_name from module_course,course where language_id = #{languageId} and priority = #{priority} " +
            "and module_course.course_id = course.course_id")
    public List<String> getCoursesByModule(CourseModule courseModule);
    @Select("select course_name from module_course,course where language_id = #{courseModule.languageId} and priority = #{courseModule.priority} " +
            "and module_course.course_id = course.course_id and ((level >= #{smallLevel} and level <= #{bigLevel}) or level = #{mustLevel})")
    public List<String> getCoursesByModuleAndRange(CourseModule courseModule, Integer smallLevel, Integer bigLevel, Integer mustLevel);

    //table user_favors_course
    @Insert("insert into user_favors_course(user_id,course_id,time) values(#{userId},#{courseId},#{time})")
    public int addFavorInfo(Long userId, Integer courseId, Date time);

    @Select("select count(*) from user_favors_course where user_id = #{userId} and course_id = #{courseId}")
    public Boolean ifFavor(Long userId, Integer courseId);

    @Delete("delete from user_favors_course where user_id = #{userId} and course_id = #{courseId}")
    public int deleteFavorInfo(Long userId, Integer courseId);


}
