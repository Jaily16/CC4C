package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 映射 course 表并使用自增主键；favorsNum 是查询附加值，不映射持久化列。 */
@Data
@TableName("course")
public class CourseEntity {
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
