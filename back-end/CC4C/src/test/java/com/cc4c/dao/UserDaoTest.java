package com.cc4c.dao;

import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserDaoTest {
    @Autowired
    private UserDao userDao;

    @Test
    void testSave(){
        User user = new User();
        user.setUserName("111");
        user.setEmail("111qq.com");
        user.setPassword("123456");
        user.setMajor(1);
        user.setAvatar("212");
        user.setState(2);
        user.setCreateTime("2022-01-10");
        user.setFavouriteLanguage(1);
        userDao.insert(user);
    }
}
