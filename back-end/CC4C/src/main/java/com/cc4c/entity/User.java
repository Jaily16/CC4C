package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
  @TableId(value = "user_id")
  @JsonSerialize(using = ToStringSerializer.class)
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
  @TableLogic(value="0",delval="1")
  //value为正常数据的值，delval为删除数据的值
  private Integer deleted;
  @TableField(exist = false)
  private String newPassword;
}
