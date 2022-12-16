package com.cc4c.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CourseDaoTest {
    @Autowired
    private CourseDao courseDao;

    @Test
    void testGetVideo(){
        System.out.println(courseDao.getCourseVideosByName("2022年新版SSM教程"));
    }
}
