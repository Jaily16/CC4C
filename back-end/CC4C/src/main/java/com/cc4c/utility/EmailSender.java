package com.cc4c.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

//邮箱信息发送类
@Component
public class EmailSender {
  @Autowired
  private JavaMailSender javaMailSender;

  public Boolean send(String code, String from, String to) {
    try {
      SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
      simpleMailMessage.setSubject("Verification Code");
      simpleMailMessage.setText("Your verification code is " + code);
      simpleMailMessage.setFrom(from);
      simpleMailMessage.setTo(to);
      javaMailSender.send(simpleMailMessage);
      return true;
    } catch (MailException e) {
      e.printStackTrace();
      return false;
    }
  }


}
