package com.cc4c.service;

import com.cc4c.entity.Result;
import org.springframework.stereotype.Service;

@Service
public interface AdminService {
  /**
   * 处理管理员登录操作，返回登录状态
   * @param name 管理员账号
   * @param password 管理员密码
   * @return 登录是否成功的结果
   */
  public Result login(String name, String password);

}
