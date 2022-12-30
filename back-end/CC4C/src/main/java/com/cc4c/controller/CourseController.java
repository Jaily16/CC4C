package com.cc4c.controller;

import com.alibaba.fastjson.JSONObject;
import com.cc4c.entity.Code;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.Result;
import com.cc4c.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/courses")
public class CourseController {
    @Autowired
    private CourseService courseService;

    @GetMapping("/home")
    private Result getHomeCourses(){
        return courseService.getHomeCourses();
    }

    @GetMapping("/recommend/{language}/{major}")
    private Result getRecommendedCourses(@PathVariable("language") Integer languageId,
                                         @PathVariable("major") Integer userMajor){
        return courseService.recommendCourseToUser(languageId, userMajor);
    }

    @GetMapping("/{name}")
    private Result getCourseByName(@PathVariable("name") String courseName){
        return courseService.getCourseByName(courseName);
    }

    @GetMapping("/star/{userId}/{courseId}")
    private Result addFavor(@PathVariable("userId") Long userId, @PathVariable("courseId") Integer courseId){
        Boolean flag = courseService.favorCourse(userId, courseId);
        if (flag){
            return new Result(Code.SUCCESS.getCode(), flag, "课程收藏成功");
        }
        else
            return new Result(Code.FAIL.getCode(), flag, "课程收藏成功");
    }

    @GetMapping("/ifFavor/{userId}/{courseId}")
    private Result ifFavorCourse(@PathVariable("userId") Long userId, @PathVariable("courseId") Integer courseId){
        Boolean flag = courseService.ifFavor(userId, courseId);
        if (flag){
            return new Result(Code.SUCCESS.getCode(), flag, "课程已收藏");
        }
        else
            return new Result(Code.FAIL.getCode(), flag, "课程为收藏");
    }

    @DeleteMapping ("/deleteFavor/{userId}/{courseId}")
    private Result deleteFavor(@PathVariable("userId") Long userId, @PathVariable("courseId") Integer courseId){
        Boolean flag = courseService.cancelFavor(userId, courseId);
        if (flag){
            return new Result(Code.SUCCESS.getCode(), flag, "课程取消收藏成功");
        }
        else
            return new Result(Code.FAIL.getCode(), flag, "课程取消收藏失败");
    }

    @GetMapping("/favorList/{id}")
    private Result getFavorCourseList(@PathVariable("id") Long userId){
        List<JSONObject> courseList = new ArrayList<>();
        List<Course> favorCourseList = courseService.getFavorCourseList(userId);
        for(Course course : favorCourseList){
            JSONObject json = new JSONObject();
            json.put("courseName", course.getCourseName());
            json.put("languageName", course.getLanguageName());
            courseList.add(json);
        }
        return new Result(Code.COURSE_GET_FAVOR_COURSE_LIST_SUCCESS.getCode(), courseList);
    }

    @GetMapping("/search/{info}")
    private Result searchCourses(@PathVariable("info") String searchInfo){
        return courseService.searchCourse(searchInfo);
    }

    @GetMapping("/language/{name}")
    private Result getCoursesByLanguage(@PathVariable("name") String languageName){
        return courseService.getCoursesByLanguage(languageName);
    }

    //admin操作
    @PostMapping("/module")
    private Result addCourseModule(@RequestBody CourseModule courseModule){
        Code code = courseService.addCourseModule(courseModule);
        if(code == Code.MODULE_PRIORITY_REPEATED){
            return new Result(code.getCode(),false, "模块优先级重复!");
        }
        return new Result(code.getCode(),true, "课程模块添加成功");
    }

    @GetMapping("/module/{id}")
    private Result getCourseModules(@PathVariable("id") Integer languageId){
        return courseService.getCourseModule(languageId);
    }

    @PostMapping("/add")
    private Result addCourse(@RequestBody Course course){
        return courseService.addCourse(course);
    }
}
