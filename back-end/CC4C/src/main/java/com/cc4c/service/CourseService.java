package com.cc4c.service;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ModuleCourse;

import java.util.List;

public interface CourseService {
    /**
     * 添加课程操作，返回添加课程的状态
     * @param course 课程对象
     * @return 用code记录返回状态
     */
    public Result addCourse(Course course);

    /**
     * 添加课程模块操作，返回添加课程模块的状态
     * @param courseModule 课程模块
     * @return 添加课程模块是否成功的状态码
     */
    public Code addCourseModule(CourseModule courseModule);

    /**
     * 将课程添加进课程模块操作，返回是否添加成功
     * @param moduleCourse 模块课程
     * @return 将课程添加进入课程模块的是否成功
     */
    public Boolean addCourseIntoModule(ModuleCourse moduleCourse);

    /**
     * 根据课程名删除课程，返回删除是否成功
     * @param courseName 课程名
     * @return 删除是否成功
     */
    public Boolean deleteCourseByName(String courseName);

    /**
     * 删除课程模块，返回是否删除成功
     * @param courseModule 课程模块
     * @return 删除是否成功
     */
    public Boolean deleteCourseModule(CourseModule courseModule);

    /**
     * 删除模块中的课程，返回是否删除成功
     * @param moduleCourse 模块课程
     * @return 删除是否成功
     */
    public Boolean deleteCourseFormModule(ModuleCourse moduleCourse);

    /**
     * 更新课程信息操作，返回更新课程的状态
     * @param course 课程
     * @return 课程信息更新是否成功的状态码
     */
    public Code updateCourse(Course course);

    /**
     * 获取课程详细信息操作，返回获取课程的结果
     * @param courseName 课程名
     * @return 课程详细信息的结果
     */
    public Result getCourseByName(String courseName);

    /**
     * 获取课程模块操作，返回获取课程模块结果
     * @param languageId 编程语言id
     * @return 获取到课程模块列表结果
     */
    public Result getCourseModule(Integer languageId);

    /**
     * 根据课程名字查找其从属的课程模块，返回获取课程模块结果
     * @param courseName 课程名称
     * @return 获取到的课程模块结果
     */
    public Result getCourseModuleByCourseName(String courseName);


    /**
     * 搜索课程操作，传入的字段为搜索字段，可按课程名模糊匹配或语言名精确匹配，返回搜索的结果
     * @param searchInfo 搜索字段
     * @return 搜索到的课程列表结果
     */
    public Result searchCourse(String searchInfo);

    /**
     * 根据用户的专业推荐课程
     * @param languageId 编程语言id
     * @param major 用户专业
     * @return 智能推荐的课程列表结果
     */
    public Result recommendCourseToUser(Integer languageId, Integer major);

    /**
     * 收藏课程，返回是否收藏成功
     * @param userId 用户id
     * @param courseId 课程id
     * @return 收藏课程是否成功的结果
     */
    public Boolean favorCourse(Long userId, Integer courseId);

    /**
     * 删除某个用户收藏的某个课程
     * @param userId 用户id
     * @param courseId 课程id
     * @return 取消课程收藏是否成功的结果
     */
    public Boolean cancelFavor(Long userId, Integer courseId);

    /**
     * 判断某个用户是否收藏某个课程
     * @param userId 用户id
     * @param courseId 课程id
     * @return 用户是否收藏了某个课程
     */
    public Boolean ifFavor(Long userId, Integer courseId);

    /**
     * 获取用户收藏的课程列表
     * @param userId 用户id
     * @return 获取到的用户收藏的课程列表结果
     */
    public List<Course> getFavorCourseList(Long userId);

    /**
     * 根据编程语言获取所有课程列表
     * @param languageName 编程语言名称
     * @return 根据编程语言获取到的相关课程列表结果
     */
    public Result getCoursesByLanguage(String languageName);

    /**
     * 根据收藏数为用户推荐课程
     * @return 根据收藏数为用户推荐课程列表结果
     */
    public Result getHomeCourses();

}
