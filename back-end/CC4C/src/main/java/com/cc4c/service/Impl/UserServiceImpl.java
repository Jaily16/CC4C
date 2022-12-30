package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc4c.entity.Blog;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.User;
import com.cc4c.service.UserService;
import com.cc4c.utility.BlogState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {
  @Autowired
  private UserDao userDao;

  @Override
  public Result register(User user) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getName, user.getName());
    if (userDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.REGISTER_FAIL.getCode(), false, "用户名重复");
    }
    lambdaQueryWrapper.eq(User::getEmail, user.getEmail());
    if (userDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.REGISTER_FAIL.getCode(), false, "该邮箱已经被注册");
    }
    user.setTime(new Date());
    try {
      userDao.insert(user);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(Code.FOREIGN_KEY_CONSTRAINT_VIOLATION.getCode(), false, "Violate Foreign Key Constraints!");
    }
    return new Result(Code.SUCCESS.getCode(), true, "注册成功");
  }

  @Override
  public Result findPassword(User user) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, user.getEmail());
    User u = userDao.selectOne(lambdaQueryWrapper);
    if(u == null){
      return new Result(Code.FAIL.getCode(), false, "该用户不存在");
    }
    if(Objects.equals(u.getPassword(), user.getNewPassword())){
      return new Result(Code.FAIL.getCode(), false, "新密码与原密码相同");
    }
    u.setPassword(user.getNewPassword());
    userDao.updateById(u);
    return new Result(Code.SUCCESS.getCode(), true, "新密码设置成功");
  }

  @Override
  public Result changePassword(User user) {
    User u = userDao.selectById(user.getId());
    if(!Objects.equals(user.getPassword(), u.getPassword())){
      return new Result(Code.FAIL.getCode(), false, "原密码输入错误");
    }
    u.setPassword(user.getNewPassword());
    userDao.updateById(u);
    return new Result(Code.SUCCESS.getCode(), true, "新密码设置成功");
  }

  @Override
  public Result login(String email, String password) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, email);
    User user = userDao.selectOne(lambdaQueryWrapper);
    if (user == null) {
      return new Result(Code.LOGIN_FAIL.getCode(), false, "该邮箱未注册!");
    }
    user = userDao.selectOne(lambdaQueryWrapper.eq(User::getEmail, email));
    if (!Objects.equals(user.getPassword(), password)) {
      System.out.println(user);
      System.out.println(password);
      return new Result(Code.LOGIN_FAIL.getCode(), false, "密码错误!");
    }
    return new Result(Code.SUCCESS.getCode(), true, "登录成功!");
  }


  // TODO : add id_fail code
  public Result update(User user) {
    try {
      User u = userDao.selectById(user.getId());
      if(!Objects.equals(user.getName(), u.getName())){
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getName, user.getName());
        if (userDao.exists(lambdaQueryWrapper)) {
          return new Result(Code.REGISTER_FAIL.getCode(), false, "用户名重复");
        }
      }
      userDao.updateById(user);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(Code.FOREIGN_KEY_CONSTRAINT_VIOLATION.getCode(), false, "修改信息异常");
    }
    return new Result(Code.SUCCESS.getCode(), true, "修改用户信息成功");
  }


  @Override
  public String getUserNameById(Long userId){
    return userDao.getUserNameById(userId);
  }

  @Override
  public User getUserByEmail(String email) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, email);
    return userDao.selectOne(lambdaQueryWrapper);
  }

}
