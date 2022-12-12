package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName(value = "user")
public class User {
  @TableId(value = "user_id", type = IdType.ASSIGN_ID)
  private Long id;
  @TableField(value = "user_name")
  private String name;
  private String email;
  private String password;
  private Integer major;
  private String avatar;
  private Integer state;
  @TableField(value = "create_time")
  private Date time;
  @TableField(value = "favourite_language")
  private Integer language;
}
