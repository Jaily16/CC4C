package com.cc4c.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.dao.AdminDao;
import com.cc4c.dao.BlogDao;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.Administrator;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import com.cc4c.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AdminServiceImpl implements AdminService {
  @Autowired
  private AdminDao adminDao;

  @Autowired
  private UserDao userDao;

  @Autowired
  private BlogDao blogDao;

  public Result login(String admin_id, String admin_password) {
    LambdaQueryWrapper<Administrator> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    lambdaQueryWrapper.eq(Administrator::getAdminId, admin_id);
    Administrator admin = adminDao.selectOne(lambdaQueryWrapper);
    if (admin == null) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "AdminId Not Correct");
    }
    if (!Objects.equals(admin.getAdminPassword(), admin_password)) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Password Incorrect!");
    }
    StpUtil.login(admin.getAdminId());
    return new Result(Code.SUCCESS.getCode(), 1, "Login Success!");
  }

  public Result logout() {
    StpUtil.logout();
    return new Result(Code.SUCCESS.getCode(), 1, "Logout Success!");
  }



  public Result kickout(String user_name) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    lambdaQueryWrapper.eq(User::getName, user_name);
    User user = userDao.selectOne(lambdaQueryWrapper);
    StpUtil.kickout(user.getId());
    return new Result(Code.SUCCESS.getCode(), 1, "Logout Success!");
  }
}
