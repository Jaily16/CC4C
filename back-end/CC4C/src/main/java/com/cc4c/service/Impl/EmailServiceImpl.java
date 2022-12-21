package com.cc4c.service.Impl;

import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.service.EmailService;
import com.cc4c.utility.EmailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailServiceImpl implements EmailService {
  public Result email(String email) {
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 4; ++i) {
      code.append(new Random().nextInt(10));
    }
    EmailSender emailSender = new EmailSender();
    if (emailSender.send(code.toString(), "1669812103@qq.com", email)) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Fail to Send Email!");
    }
    return new Result(Code.SUCCESS.getCode(), code.toString(), "Send Email Success!");
  }

}
