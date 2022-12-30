package com.cc4c.service;

import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ModuleCourse;
import com.cc4c.utility.CourseLevel;
import com.cc4c.utility.ModuleLevel;
import com.cc4c.utility.UserMajor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
public class CourseServiceTest {
    @Autowired
    private CourseService courseService;

    @Test
    void testAdd(){
        Course course = new Course();
        Integer id = 61;
        course.setCourseId(id);
        course.setLanguageName("python");
        course.setCourseName("黑马_2022新版python8天入门到精通");
        course.setDescription("default");
        course.setLevel(CourseLevel.EASY_AND_DEFAULT.getLevel());
        course.setState(1);
        System.out.println(courseService.addCourse(course));
        ModuleCourse moduleCourse = new ModuleCourse();
        moduleCourse.setLanguageId(3);
        moduleCourse.setPriority(1);
        moduleCourse.setCourseId(id);
        System.out.println(courseService.addCourseIntoModule(moduleCourse));
    }

    @Test
    void testDelete(){
        System.out.println(courseService.deleteCourseByName("2022年新版SSM教程"));
    }

    @Test
    void testUpdate(){
        Course course = new Course();
        course.setLanguageName("C++");
        course.setCourseName("C语言和C++简介");
        course.setDescription("这里是课程内容");
        course.setLevel(CourseLevel.EASY.getLevel());
        course.setState(1);
        System.out.println(courseService.updateCourse(course));
    }

    @Test
    void testGetOneByName(){
        System.out.println(courseService.getCourseByName("20天Java入门基础视频教程"));
    }

    @Test
    void testSearch(){
        System.out.println(courseService.searchCourse("java"));
    }

    @Test
    void testModule(){
        CourseModule module = new CourseModule();
        module.setLanguageId(3);
        module.setModuleName("python高级");
        module.setPriority(3);
        module.setLevel(ModuleLevel.DEFAULT.getLevel());
        System.out.println(courseService.addCourseModule(module));
    }

    @Test
    void testCourseIntoModule(){
        ModuleCourse moduleCourse = new ModuleCourse();
        moduleCourse.setLanguageId(2);
        moduleCourse.setPriority(3);
        moduleCourse.setCourseId(14);
        System.out.println(courseService.addCourseIntoModule(moduleCourse));
    }

    @Test
    void testGetCourseModule(){
//        System.out.println(courseService.getCourseModule(1));
//        System.out.println(courseService.getCourseModuleByCourseName("2022年新版SSM全套教程"));
        System.out.println(courseService.recommendCourseToUser(3, UserMajor.MAJOR_IN_CS.getMajor()));
    }

    @Test
    void testFavor(){
        System.out.println(courseService.favorCourse(1605928940969508866L, 3));
    }

    @Test
    void testGet(){
        System.out.println(courseService.getHomeCourses());
    }
}
