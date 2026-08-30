package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("user")
class UserEntity {
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
