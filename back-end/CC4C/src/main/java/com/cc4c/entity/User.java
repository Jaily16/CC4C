package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class User {
    @TableId
    private Long userId;
    private String userName;
    private String email;
    private String password;
    private Integer major;
    private String avatar;
    private Integer state;
    private String createTime;
    private Integer favouriteLanguage;
}
