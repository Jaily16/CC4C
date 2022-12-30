package com.cc4c.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc4c.entity.Blog;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import com.cc4c.service.EmailService;
import com.cc4c.service.UserService;
import com.cc4c.utility.BlogState;
import com.cc4c.utility.FileUtils;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URLDecoder;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/users")
public class UserController {
  @Value("${cc4c.save-avatar-path}")
  private String saveAvatarPath;

  @Value("${cc4c.request-avatar-path}")
  private String requestAvatarPath;
  @Autowired
  private UserService userService;

  @Autowired
  private EmailService emailService;

  @PostMapping("/uploadAvatar")
  public Result setAvatar(@RequestParam(value = "file") MultipartFile file){
    Map<String, String> paths = FileUtils.uploadAvatar(file,
            saveAvatarPath,
            Objects.requireNonNull(file.getOriginalFilename()),
            requestAvatarPath);
    System.out.println(paths);
    return new Result(Code.SUCCESS.getCode(), paths);
  }

  @PostMapping("/register")
  public Result register(@RequestBody User user) {
    System.out.println(user.getName() + " registering ...");
    return userService.register(user);
  }

  @PutMapping("/password/forget")
  public Result forgetPassword(@RequestBody User user) {
    return userService.findPassword(user);
  }

  @PutMapping("/password/change")
  public Result changePassword(@RequestBody User user){
    return userService.changePassword(user);
  }

  @PostMapping("/login")
  public Result login(@RequestBody User user, HttpServletResponse resp) {
    Result login = userService.login(user.getEmail(), user.getPassword());
    if(Objects.equals(login.getCode(), Code.SUCCESS.getCode())){
      Cookie cookie = new Cookie("user_email", user.getEmail());
      // 设置cookie保存时间为两个小时
      cookie.setMaxAge(60 * 60 * 2);
      resp.addCookie(cookie);
    }
    return login;
  }

  @GetMapping("/logout")
  private Result logout(HttpServletResponse resp){
    Cookie cookie = new Cookie("user_email", "");
    resp.addCookie(cookie);
    return new Result(Code.SUCCESS.getCode(), true);
  }


  @PutMapping("/update")
  public Result update(@RequestBody User user) {
    System.out.println(user.getName() + " modifying ...");
    return userService.update(user);
  }

  @GetMapping("/email/{email}")
  public Result email(@PathVariable("email") String email) {
    System.out.println(email + " sending ...");
    return emailService.email(email);
  }


  @GetMapping("/verify")
  private Result verifyUser(@CookieValue(value = "user_email", defaultValue = "1234567890123") String email){
    if("1234567890123".equals(email)){
      return new Result(Code.FAIL.getCode(), false, "请先登录");
    }
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @GetMapping("/info")
  private Result getUserInfo(@CookieValue(value = "user_email") String email){
    User user = userService.getUserByEmail(email);
    return new Result(Code.SUCCESS.getCode(), user);
  }

}
