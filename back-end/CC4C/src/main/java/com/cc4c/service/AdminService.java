package com.cc4c.service;

import com.cc4c.entity.Result;
import org.springframework.stereotype.Service;

@Service
public interface AdminService {
  public Result login(String name, String password);

  public Result logout();


  public Result kickout(String user_name);
}
