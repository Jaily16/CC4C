package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.controller.Code;
import com.cc4c.controller.Result;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.User;
import com.cc4c.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {
  @Autowired
  private UserDao userDao;


  public Result register(User user) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    lambdaQueryWrapper.eq(User::getUserName, user.getUserName()).or().eq(User::getEmail, user.getEmail());
    if (userDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.REGISTER_FAIL.getCode(), 0, "Username Exists!");
    }
    userDao.insert(user);
    return new Result(Code.SUCCESS.getCode(), 1, "Register Success!");

  }

  public Result login(String name, String password) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    lambdaQueryWrapper.eq(User::getUserName, name);
    User user = userDao.selectOne(lambdaQueryWrapper);
    if (user == null) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Username Not Exists!");
    }
    if (!Objects.equals(user.getPassword(), password)) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Password Incorrect!");
    }
    return new Result(Code.SUCCESS.getCode(), 1, "Login Success!");
  }

}
