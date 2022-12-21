package com.cc4c.service.Impl;

import cn.dev33.satoken.stp.StpInterface;
import com.cc4c.dao.AdminDao;
import com.cc4c.dao.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义权限验证接口扩展
 */
@Component    // 保证此类被SpringBoot扫描，完成Sa-Token的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

  @Autowired
  private UserDao userDao;

  @Autowired
  private AdminDao adminDao;

  /**
   * 返回一个账号所拥有的权限码集合
   */
  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    // TODO
    return null;
  }

  /**
   * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
   */
  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    List<String> list = new ArrayList<String>();
    Integer id = Integer.parseInt(String.valueOf(loginId));
    if (userDao.selectById(id) != null) {
      list.add("user");
    }
    if (adminDao.selectById(String.valueOf(loginId)) != null) {
      list.add("admin");
    }
    return list;
  }

}
