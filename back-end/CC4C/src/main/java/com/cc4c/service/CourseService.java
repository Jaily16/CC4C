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
     * @return 用code记录饭返回状态
     */
    public Code addCourse(Course course);

    /**
     * 添加课程模块操作，返回添加课程模块的状态
     * @param courseModule
     * @return
     */
    public Code addCourseModule(CourseModule courseModule);

    /**
     * 将课程添加进课程模块操作，返回是否添加成功
     * @param moduleCourse
     * @return
     */
    public Boolean addCourseIntoModule(ModuleCourse moduleCourse);

    /**
     * 根据课程名删除课程，返回删除是否成功
     * @param courseName
     * @return
     */
    public Boolean deleteCourseByName(String courseName);

    /**
     * 删除课程模块，返回是否删除成功
     * @param courseModule
     * @return
     */
    public Boolean deleteCourseModule(CourseModule courseModule);

    /**
     * 删除模块中的课程，返回是否删除成功
     * @param moduleCourse
     * @return
     */
    public Boolean deleteCourseFormModule(ModuleCourse moduleCourse);

    /**
     * 更新课程信息操作，返回更新课程的状态
     * @param course
     * @return
     */
    public Code updateCourse(Course course);

    /**
     * 获取课程操作，返回获取课程的结果
     * @param courseName
     * @return
     */
    public Result getCourseByName(String courseName);

    /**
     * 获取课程模块操作，返回获取课程模块结果
     * @param languageId
     * @return
     */
    public Result getCourseModule(Integer languageId);

    /**
     * 根据课程名字查找其从属的课程模块，返回获取课程模块结果
     * @param courseName
     * @return
     */
    public Result getCourseModuleByCourseName(String courseName);


    /**
     * 搜索课程操作，传入的字段为搜索字段，可按课程名模糊匹配或语言名精确匹配，返回搜索的结果
     * @param searchInfo
     * @return
     */
    public Result searchCourse(String searchInfo);

    public Result recommendCourseToUser(Integer languageId, Integer major);
    public Boolean favorCourse(Long userId, Integer courseId);

    /**
     * 删除某个用户收藏的某个课程
     * @param userId
     * @param courseId
     * @return
     */
    public Boolean cancelFavor(Long userId, Integer courseId);

    /**
     * 判断某个用户是否收藏某个课程
     * @param userId
     * @param courseId
     * @return
     */
    public Boolean ifFavor(Long userId, Integer courseId);

    /**
     * 获取用户收藏的课程列表
     * @param userId
     * @return
     */
    public List<Course> getFavorCourseList(Long userId);

}
