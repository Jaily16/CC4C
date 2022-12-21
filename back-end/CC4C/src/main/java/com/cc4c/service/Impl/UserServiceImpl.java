package com.cc4c.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.User;
import com.cc4c.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

@Service
public class UserServiceImpl implements UserService {
  @Autowired
  private UserDao userDao;

  @Override
  public Result register(User user) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getName, user.getName());
    if (userDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.REGISTER_FAIL.getCode(), 0, "Username Exists!");
    }
    lambdaQueryWrapper.eq(User::getEmail, user.getEmail());
    if (userDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.REGISTER_FAIL.getCode(), 0, "Email Exists!");
    }
    user.setTime(new Date());
    user.setState(0);
    try {
      userDao.insert(user);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(Code.FOREIGN_KEY_CONSTRAINT_VIOLATION.getCode(), 0, "Violate Foreign Key Constraints!");
    }
    return new Result(Code.SUCCESS.getCode(), 1, "Register Success!");
  }

  @Override
  public Result login(String email, String password) {
    LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, email);
    User user = userDao.selectOne(lambdaQueryWrapper);
    if (user == null) {
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Username Not Exists!");
    }
    user = userDao.selectOne(lambdaQueryWrapper.eq(User::getPassword, password));
    if (!Objects.equals(user.getPassword(), password)) {
      System.out.println(user);
      System.out.println(password);
      return new Result(Code.LOGIN_FAIL.getCode(), 0, "Password Incorrect!");
    }
    StpUtil.login(user.getId());
    return new Result(Code.SUCCESS.getCode(), 1, "Login Success!");
  }


  @Override
  public Result logout() {
    if (!StpUtil.isLogin()) {
      return new Result(Code.SUCCESS.getCode(), 0, "No Login!");
    }
    StpUtil.logout();
    return new Result(Code.SUCCESS.getCode(), 1, "Logout Success!");
  }

  // TODO : add id_fail code
  public Result update(User user) {
    if (!check_identity(user.getId())) {
      return new Result(Code.FAIL.getCode(), 1, "No Accessibility!");
    }
    try {
      userDao.updateById(user);
    } catch (Exception e) {
      e.printStackTrace();
      return new Result(Code.FOREIGN_KEY_CONSTRAINT_VIOLATION.getCode(), 0, "Violate Foreign Key Constraints!");
    }
    return new Result(Code.SUCCESS.getCode(), 1, "Logout Success!");
  }

  @Value("${cc4c.upload-path}")
  private String uploadPath;
  public static final ArrayList<String> AVATAR_TYPE = new ArrayList<>();

  static {
    AVATAR_TYPE.add("image/jpeg");
    AVATAR_TYPE.add("image/png");
    AVATAR_TYPE.add("image/bmp");
    AVATAR_TYPE.add("image/gif");
  }

  @Override
  public Result uploadAvatar(Long id, MultipartFile multipartFile) {
    if (multipartFile.isEmpty()) {
      return new Result(Code.FAIL.getCode(), 0, "Please Upload an Image!");
    }
    if (!AVATAR_TYPE.contains(multipartFile.getContentType())) {
      return new Result(Code.FAIL.getCode(), 0, "Invalid Image File!");
    }

    File folder = new File(uploadPath);
    if (!folder.isDirectory()) {
      folder.mkdir();
    }

    // 对上传的文件重命名, 避免文件重名
    String oldName = multipartFile.getOriginalFilename();
    assert oldName != null;
    String newName = id + oldName.substring(oldName.lastIndexOf("."));
    try {
      multipartFile.transferTo(new File(folder, newName));
    } catch (IOException e) {
      return new Result(Code.FAIL.getCode(), 0, "System Error!");
    }
    LambdaUpdateWrapper<User> lambdaUpdateWrapper = new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getAvatar, uploadPath + "/avatar/" + newName);
    userDao.update(null, lambdaUpdateWrapper);
    return new Result(Code.SUCCESS.getCode(), 0, "Avatar Upload Success!");
  }

  @Override
  public Result isLogin() {
    return new Result(Code.SUCCESS.getCode(), StpUtil.isLogin(), "Check Over!");
  }

  public Boolean check_identity(Long id) {
    return StpUtil.getLoginIdDefaultNull() == id;
  }

  @Override
  public String getUserNameById(Long userId){
    return userDao.getUserNameById(userId);
  }

}
