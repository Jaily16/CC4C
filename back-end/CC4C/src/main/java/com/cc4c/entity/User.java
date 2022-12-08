package com.cc4c.entity;

import lombok.Data;

@Data
public class User {
    private Integer userId;
    private String userName;
    private String email;
    private Integer major;
    private String avatar;
    private Integer state;
    private String createTime;
    private Integer favouriteLanguage;
}
