package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 映射 administrator 表中的管理员编号、密码及逻辑删除标记；不直接作为接口响应。 */
@Data
@TableName("administrator")
public class AdministratorEntity {
    @TableId("admin_id")
    private String adminId;

    private String adminPassword;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
