package com.cc4c.service;

import com.cc4c.entity.Comment;
import com.cc4c.utility.CommentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CommentServiceTest {
    @Autowired
    private CommentService commentService;

    @Test
    void testComment(){
        Comment comment = new Comment();
//        comment.setUserId(1603284684677148674L);
//        comment.setUserId(1603284684677148674L);
        comment.setUserId(1602639796138680322L);
        comment.setContent("我是对评论的评论");
        comment.setFatherId(1604827993379729409L);
        System.out.println(commentService.comment(comment, CommentType.INDIRECT_COMMENT.getType()));
    }

    @Test
    void testGet(){
        System.out.println(commentService.getBlogComments(1L));
    }
}
