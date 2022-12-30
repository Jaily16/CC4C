package com.cc4c.service.Impl;

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

  public Result login(String admin_id, String admin_password) {
    LambdaQueryWrapper<Administrator> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    lambdaQueryWrapper.eq(Administrator::getAdminId, admin_id);
    Administrator admin = adminDao.selectOne(lambdaQueryWrapper);
    if (admin == null) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "不存在该管理员账号");
    }
    if (!Objects.equals(admin.getAdminPassword(), admin_password)) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "密码错误");
    }
    return new Result(Code.SUCCESS.getCode(), 1, "管理员登录成功");
  }

}
