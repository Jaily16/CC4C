package com.cc4c.service;

import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
  public Result register(User user);

  public Result login(String name, String password);

  public Result logout();

  public Result update(User user);

  public Result uploadAvatar(Long id, MultipartFile multipartFile);

  public Result isLogin();

  public String getUserNameById(Long userId);
}
