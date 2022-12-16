package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.controller.Code;
import com.cc4c.controller.Result;
import com.cc4c.dao.CourseDao;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.CourseVideo;
import com.cc4c.entity.ModuleCourse;
import com.cc4c.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {
    @Autowired
    private CourseDao courseDao;
    @Override
    public Code addCourse(Course course) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, course.getCourseName());
        if(courseDao.exists(lambdaQueryWrapper)){
            return Code.COURSE_NAME_REPEATED;
        }
        if(courseDao.insert(course) <= 0){
            return Code.COURSE_ADD_FAILED;
        }
        //填充视频表
        List<CourseVideo> courseVideos = course.getCourseVideos();
        for (CourseVideo courseVideo: courseVideos) {
            if(courseDao.addCourseVideo(course.getCourseName(),courseVideo.getPlatform(),courseVideo.getUrl()) <= 0){
                return Code.COURSE_ADD_VIDEO_FAILED;
            }
        }
        return Code.COURSE_ADD_SUCCESS;
    }

    @Override
    public Code addCourseModule(CourseModule courseModule) {
        if(courseDao.countCourseModule(courseModule)){
            return Code.MODULE_PRIORITY_REPEATED;
        }
        return (courseDao.addCourseModule(courseModule) > 0) ? Code.COURSE_ADD_MODULE_SUCCESS : Code.COURSE_ADD_MODULE_FAILED;
    }

    @Override
    public Boolean addCourseIntoModule(ModuleCourse moduleCourse) {
        return courseDao.addModuleCourse(moduleCourse) > 0;
    }

    @Override
    public Boolean deleteCourseByName(String courseName) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, courseName);
        return courseDao.delete(lambdaQueryWrapper) > 0;
    }

    @Override
    public Boolean deleteCourseModule(CourseModule courseModule) {
        courseDao.deleteModuleCourseByModule(courseModule.getLanguageId(), courseModule.getPriority());
        return courseDao.deleteCourseModule(courseModule) > 0 ;
    }

    @Override
    public Boolean deleteCourseFormModule(ModuleCourse moduleCourse) {
        return courseDao.deleteModuleCourse(moduleCourse) > 0;
    }

    @Override
    public Code updateCourse(Course course) {
        String oldName = courseDao.getCourseNameById(course.getCourseId());
        if(!course.getCourseName().equals(oldName)){
            //若修改了课程名则检测课程名是否重复
            LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(Course::getCourseName, course.getCourseName());
            if(courseDao.exists(lambdaQueryWrapper)){
                return Code.COURSE_NAME_REPEATED;
            }
        }
        //删除先前的视频表
        if(courseDao.deleteCourseVideoByName(oldName) <= 0){
            return Code.COURSE_UPDATE_VIDEO_FAILED;
        }
        //修改课程信息
        if(courseDao.updateById(course) <= 0){
            return Code.COURSE_UPDATE_FAILED;
        }
        //填充视频表
        List<CourseVideo> courseVideos = course.getCourseVideos();
        for (CourseVideo courseVideo: courseVideos) {
            if(courseDao.addCourseVideo(course.getCourseName(),courseVideo.getPlatform(),courseVideo.getUrl()) <= 0){
                return Code.COURSE_ADD_VIDEO_FAILED;
            }
        }
        return Code.COURSE_UPDATE_SUCCESS;
    }

    @Override
    public Result getCourseByName(String courseName) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Course::getCourseName, courseName);
        Course course = courseDao.selectOne(lambdaQueryWrapper);
        if(course == null){
            return new Result(Code.COURSE_GET_ONE_FAILED.getCode(), null, "course not exist");
        }
        List<CourseVideo> videoList = courseDao.getCourseVideosByName(courseName);
        course.setCourseVideos(videoList);
        return new Result(Code.COURSE_GET_ONE_SUCCESS.getCode(), course);
    }

    @Override
    public Result getCourseModule(Integer languageId) {
        List<CourseModule> modules = courseDao.getCourseModulesByLId(languageId);
        if(modules == null || modules.isEmpty()){
            return new Result(Code.COURSE_GET_MODULES_FAILED.getCode(), null, "get modules failed");
        }
        for(CourseModule module : modules){
            List<String> courses = courseDao.getCoursesByModule(module);
            module.setCourseList(courses);
        }
        return new Result(Code.COURSE_GET_MODULES_SUCCESS.getCode(), modules);
    }

    @Override
    public Result getCourseModuleByCourseName(String courseName) {
        CourseModule module = courseDao.getCourseModuleByCName(courseName);
        if(module == null){
            return new Result(Code.COURSE_GET_MODULE_BY_CNAME_FAILED.getCode(), null, "course not belonging to a module");
        }
        return new Result(Code.COURSE_GET_MODULE_BY_CNAME_SUCCESS.getCode(), module);
    }


    @Override
    public Result searchCourse(String searchInfo) {
        LambdaQueryWrapper<Course> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(Course::getCourseName, searchInfo).or().eq(Course::getLanguageName, searchInfo);
        List<Course> courses = courseDao.selectList(lambdaQueryWrapper);
        if(courses == null || courses.isEmpty()){
            return new Result(Code.COURSE_SEARCH_NO_RESULT.getCode(), null, "no such course");
        }
        return new Result(Code.COURSE_SEARCH_SUCCESS.getCode(), courses);
    }


}
