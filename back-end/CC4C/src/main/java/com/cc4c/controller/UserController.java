package com.cc4c.controller;

import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import com.cc4c.service.EmailService;
import com.cc4c.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {
  @Autowired
  private UserService userService;

  @Autowired
  private EmailService emailService;

  @PostMapping("/register")
  public Result register(@RequestBody User user) {
    System.out.println(user.getName() + " registering ...");
    return userService.register(user);
  }

  @PostMapping("/login")
  public Result login(String email, String password) {
    System.out.println(email + " logging in ...");
    return userService.login(email, password);
  }

  @PostMapping("/logout")
  public Result logout(String name) {
    System.out.println(name + " logging out ...");
    return userService.logout();
  }

  @PutMapping("/update")
  public Result update(@RequestBody User user) {
    System.out.println(user.getName() + " modifying ...");
    return userService.update(user);
  }

  @GetMapping("/email")
  public Result email(String email) {
    System.out.println(email + " sending ...");
    return emailService.email(email);
  }

  @PostMapping("/avatar")
  public Result avatar(Long id, MultipartFile multipartFile) {
    System.out.println(id + " changing avatar ...");
    return userService.uploadAvatar(id, multipartFile);
  }

  @GetMapping("/isLogin")
  public Result isLogin() {
    System.out.println("checking logging status ...");
    return userService.isLogin();
  }

  @GetMapping("/user_id")
  public Result profile(Long userId){
    // TODO
    return null;
  }
}
