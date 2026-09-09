package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 映射 user 表中的资料、密码和账户状态；包含敏感字段，不直接作为接口响应。 */
@Data
@TableName("user")
public class UserEntity {
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_name")
    private String name;

    private String email;
    private String password;
    private Integer major;
    private String avatar;
    private Integer state;

    @TableField("create_time")
    private Date time;

    @TableField("favourite_language")
    private Integer language;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
