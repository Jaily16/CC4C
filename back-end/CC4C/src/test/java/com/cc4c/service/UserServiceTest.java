package com.cc4c.service;

import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

@SpringBootTest
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @Test
    void testRegister(){
        User user = new User();
        user.setUserName("11231");
        user.setEmail("12112qq.com");
        user.setPassword("123456");
        user.setMajor(1);
        user.setAvatar("212");
        user.setState(2);
        user.setCreateTime(new Date());
        user.setFavouriteLanguage(1);
        System.out.println(userService.register(user));
    }
}
