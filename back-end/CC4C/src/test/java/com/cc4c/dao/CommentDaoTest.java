package com.cc4c.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CommentDaoTest {
    @Autowired
    private CommentDao commentDao;

    @Test
    void testGetComment(){
        System.out.println(commentDao.getCourseComments(1));
        System.out.println(commentDao.getBlogComments(1L));
        System.out.println(commentDao.getIndirectComments(1604032752850608129L));
        System.out.println(commentDao.getById(1604804312272244738L));
        System.out.println(commentDao.deleteById(1604032752850608129L));
    }
}
