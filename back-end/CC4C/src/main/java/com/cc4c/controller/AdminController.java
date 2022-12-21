package com.cc4c.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cc4c.entity.Administrator;
import com.cc4c.entity.Result;
import com.cc4c.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SaCheckRole("admin")
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public Result login(@RequestBody Administrator admin) {
        System.out.println(admin.getAdminId() + " logging in ...");
        return adminService.login(admin.getAdminId(), admin.getAdminPassword());
    }

    @PostMapping("/logout")
    public Result logout(String admin_id) {
        System.out.println(admin_id + " logging out ...");
        return adminService.logout();
    }

    @PostMapping("/kickout")
    public Result kickout(String user_name) {
        System.out.println(user_name + " is kicked out ...");
        return adminService.kickout(user_name);
    }



}

