package com.cc4c.service;

import com.cc4c.controller.Result;
import com.cc4c.entity.User;
import org.springframework.stereotype.Service;

public interface UserService {
  public Result register(User user);

  public Result login(String name, String password);
}
