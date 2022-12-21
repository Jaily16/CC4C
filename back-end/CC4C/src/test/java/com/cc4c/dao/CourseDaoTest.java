package com.cc4c.dao;

import com.cc4c.entity.CourseModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class CourseDaoTest {
    @Autowired
    private CourseDao courseDao;

    @Test
    void testGet(){
        List<CourseModule> range = courseDao.getCourseModuleByLIdAndRange(1, 0, 1);
        System.out.println(courseDao.getCoursesByModule(range.get(0)));
    }

}
