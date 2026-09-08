package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 映射身份认证数据库记录，字段语义与现有表结构保持一致。
 */
@Data
@TableName("administrator")
public class AdministratorEntity {
    @TableId("admin_id")
    private String adminId;

    private String adminPassword;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
