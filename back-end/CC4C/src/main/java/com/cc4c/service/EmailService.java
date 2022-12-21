package com.cc4c.service;

import com.cc4c.entity.Result;
import org.springframework.stereotype.Service;

@Service
public interface EmailService {
  public Result email(String email);
//  public Result expire();
}
