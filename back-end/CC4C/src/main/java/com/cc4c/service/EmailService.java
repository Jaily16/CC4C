package com.cc4c.service;

import com.cc4c.entity.Result;

public interface EmailService {
  /**
   * 给用户邮箱发送邮箱激活码
   * @param email 用户填写的邮箱
   * @return 发送的邮箱验证码以及发送是否成功的结果
   */
  public Result email(String email);
}
