package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.util.Date;

@Data
public class User {
  @TableId
  private Long userId;
  private String userName;
  private String email;
  @TableField(select = false)
  private String password;
  private Integer major;
  private String avatar;
  private Integer state;
  private Date createTime;
  private Integer favouriteLanguage;
  @TableLogic(value="0",delval="1")
  //value为正常数据的值，delval为删除数据的值
  private Integer deleted;
}
