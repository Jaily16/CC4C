package com.cc4c.service;

import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface UserService {
  public Result register(User user);

  public Result findPassword(User user);

  public Result changePassword(User user);

  public Result login(String name, String password);

  public Result update(User user);

  public String getUserNameById(Long userId);

  public User getUserByEmail(String email);
}
