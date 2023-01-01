package com.cc4c.service;

import com.cc4c.entity.Result;
import com.cc4c.entity.User;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface UserService {
  /**
   * 用户注册
   * @param user 用户
   * @return 用户是否注册成功的结果
   */
  public Result register(User user);

  /**
   * 用户找回密码
   * @param user 用户
   * @return 用户找回密码是否成功的结果
   */
  public Result findPassword(User user);

  /**
   * 用户修改密码
   * @param user 用户
   * @return 用户修改密码是否成功的结果
   */
  public Result changePassword(User user);

  /**
   * 用户登录
   * @param name 用户名(这里指用户邮箱)
   * @param password 密码
   * @return 用户登录是否成功的结果
   */
  public Result login(String name, String password);

  /**
   * 更新用户信息(用户修改个人信息)
   * @param user 用户
   * @return 用户信息更新是否成功的结果
   */
  public Result update(User user);

  /**
   * 根据用户id获取用户对象
   * @param userId 用户id
   * @return 获取到的用户对象结果
   */
  public User getUserById(Long userId);

  /**
   * 根据用户邮箱获取用户对象
   * @param email 用户邮箱
   * @return 获取到的用户对象结果
   */
  public User getUserByEmail(String email);
}
