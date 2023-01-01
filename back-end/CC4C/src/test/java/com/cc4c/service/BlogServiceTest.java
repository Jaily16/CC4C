package com.cc4c.service;

import com.cc4c.utility.BlogState;
import com.cc4c.utility.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BlogServiceTest {
    @Autowired
    private BlogService blogService;

    @Test
    void testHome(){
        System.out.println(blogService.getBlogList(BlogState.VERIFIED, OrderType.BY_CLICK));
    }
}
