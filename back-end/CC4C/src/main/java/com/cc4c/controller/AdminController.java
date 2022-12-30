package com.cc4c.controller;

import com.cc4c.entity.Administrator;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public Result login(@RequestBody Administrator admin,  HttpServletResponse resp) {
        Result result = adminService.login(admin.getAdminId(), admin.getAdminPassword());
        if(Objects.equals(result.getCode(), Code.SUCCESS.getCode())){
            Cookie cookie = new Cookie("admin", admin.getAdminId());
            // 设置cookie保存时间为1个小时
            cookie.setMaxAge(60 * 60);
            resp.addCookie(cookie);
        }
        return result;
    }

    @GetMapping("/logout")
    private Result logout(HttpServletResponse resp){
        Cookie cookie = new Cookie("admin", "");
        resp.addCookie(cookie);
        return new Result(Code.SUCCESS.getCode(), true);
    }

    @GetMapping("/verify")
    private Result verifyAdmin(@CookieValue(value = "admin", defaultValue = "38219038928391") String adminId){
        if("38219038928391".equals(adminId)){
            return new Result(Code.FAIL.getCode(), false, "请先登录");
        }
        return new Result(Code.SUCCESS.getCode(), true);
    }



}

