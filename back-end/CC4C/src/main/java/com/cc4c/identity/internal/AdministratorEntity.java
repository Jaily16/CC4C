package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("administrator")
class AdministratorEntity {
    @TableId("admin_id")
    private String adminId;
    private String adminPassword;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
