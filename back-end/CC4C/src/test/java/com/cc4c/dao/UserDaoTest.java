package com.cc4c.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

@SpringBootTest
public class UserDaoTest {
    @Autowired
    private UserDao userDao;

//    @Test
//    void testSave(){
//        User user = new User();
//        user.setUserName("1411");
//        user.setEmail("12188qq.com");
//        user.setPassword("123456");
//        user.setMajor(1);
//        user.setAvatar("212");
//        user.setState(2);
//        user.setCreateTime(new Date());
//        user.setFavouriteLanguage(1);
//        userDao.insert(user);
//    }
//
//    @Test
//    void myTest(){
//        String name = "111";
//        String pwd = "123456";
//        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
//        lambdaQueryWrapper.eq(User::getUserName, name).eq(User::getPassword, pwd);
//        System.out.println(userDao.selectOne(lambdaQueryWrapper));
//    }
}
