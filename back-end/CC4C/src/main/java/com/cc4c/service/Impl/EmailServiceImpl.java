package com.cc4c.service.Impl;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.service.EmailService;
import com.cc4c.utility.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailServiceImpl implements EmailService{
  @Autowired
  private EmailSender emailSender;

  @Override
  public Result email(String email) {
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 4; ++i) {
      code.append(new Random().nextInt(10));
    }
    if (!emailSender.send(code.toString(), "1669812103@qq.com", email)) {
      return new Result(Code.LOGIN_FAIL.getCode(), false, "Fail to Send Email!");
    }
    return new Result(Code.SUCCESS.getCode(), code.toString(), "Send Email Success!");
  }

}
