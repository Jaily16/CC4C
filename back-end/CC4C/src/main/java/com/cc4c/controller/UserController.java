package com.cc4c.controller;

import com.cc4c.entity.User;
import com.cc4c.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
  @Autowired
  private UserService userService;

  @PostMapping("/register")
  public Result register(@RequestBody User user) {
    System.out.println(user.getName() + " registering ...");
    return userService.register(user);
  }

  @PostMapping("/login")
  public Result login(String user_name, String password) {
    System.out.println(user_name + " logging in ...");
    return userService.login(user_name, password);
  }
}
