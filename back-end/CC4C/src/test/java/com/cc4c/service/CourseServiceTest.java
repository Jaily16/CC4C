package com.cc4c.service;

import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.CourseVideo;
import com.cc4c.utility.CourseLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class CourseServiceTest {
    @Autowired
    private CourseService courseService;

    @Test
    void testAdd(){
        Course course = new Course();
        course.setLanguageName("java");
        course.setCourseName("2022java基础教程 从入门到起飞");
        course.setDescription("Java基础的天花板教程，面向0基础同学，有手就行。从0开始，到进阶，最后起飞，层层递进。课程中会讲解很多编程思想，以及我是如何从0开始去分析一个问题，并把代码写出来的。拒绝一听就懂，一学就废。");
        course.setCourseBook("book.pdf");
        course.setCourseWebsite("http://yun.itheima.com/course/1002.html?capid=1$hm");
        List<CourseVideo> courseVideos = new ArrayList<CourseVideo>();
        courseVideos.add(new CourseVideo("bilibili 上部","https://www.bilibili.com/video/BV17F411T7Ao/?spm_id_from=333.999.0.0&vd_source=6368b03254ca1c1ef430df97e3015147"));
        courseVideos.add(new CourseVideo("bilibili 下部","https://www.bilibili.com/video/BV1yW4y1Y7Ms/?spm_id_from=333.999.0.0&vd_source=6368b03254ca1c1ef430df97e3015147"));
        courseVideos.add(new CourseVideo("itheima","http://yun.itheima.com/course/1002.html?capid=1$hm"));
        courseVideos.add(new CourseVideo("youtube","https://www.youtube.com/playlist?list=PLFbd8KZNbe-8cHN7zAPg8S8zrrx7pF6cZ"));
        course.setCourseVideos(courseVideos);
        course.setLevel(CourseLevel.DIFFICULT.getLevel());
        course.setState(1);
        System.out.println(courseService.addCourse(course));
    }

    @Test
    void testDelete(){
        System.out.println(courseService.deleteCourseByName("2022年新版SSM教程"));
    }

    @Test
    void testUpdate(){
        Course course = new Course();
        course.setCourseId(1);
        course.setLanguageName("java");
        course.setCourseName("2022年新版SSM全套教程");
        course.setDescription("本课程为黑马程序员的SSM最新全套SSM课程，讲解了Spring Spring MVC Mybatis的具体原理和使用方法");
        course.setCourseBook("book.pdf");
        course.setCourseWebsite("http://yun.itheima.com/course/1001.html?capid=1$hm");
        List<CourseVideo> courseVideos = new ArrayList<CourseVideo>();
        courseVideos.add(new CourseVideo("bilibili","https://www.bilibili.com/video/BV1Fi4y1S7ix?p=1&vd_source=6368b03254ca1c1ef430df97e3015147"));
        courseVideos.add(new CourseVideo("itheima","http://yun.itheima.com/course/1001.html?capid=1$hm"));
        courseVideos.add(new CourseVideo("youtube","https://www.youtube.com/playlist?list=PLFbd8KZNbe-_GnG93sgOx2EZJQi2FSQ7g"));
        course.setCourseVideos(courseVideos);
        course.setLevel(2);
        course.setState(1);
        System.out.println(courseService.updateCourse(course));
    }

    @Test
    void testGetOneByName(){
        System.out.println(courseService.getCourseByName("2022年新版SSM教程"));
    }

    @Test
    void testSearch(){
        System.out.println(courseService.searchCourse("jav"));
    }

    @Test
    void testAddModule(){
        CourseModule module = new CourseModule();
        module.setLanguageId(1);
        module.setModuleName("基础语法知识");
        module.setPriority(1);
        System.out.println(courseService.addCourseModule(module));
    }
}
