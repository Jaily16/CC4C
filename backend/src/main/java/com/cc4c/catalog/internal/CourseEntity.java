package com.cc4c.catalog.internal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 映射课程目录数据库记录，字段语义与现有表结构保持一致。
 */
@Data
@TableName("course")
class CourseEntity {
    @TableId(value = "course_id", type = IdType.AUTO)
    private Integer courseId;

    private String courseName;
    private String languageName;
    private String description;
    private Integer level;
    private Integer state;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(exist = false)
    private Integer favorsNum;
}
