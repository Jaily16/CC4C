package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.util.List;

@Data
public class Course {
    @TableId(type = IdType.AUTO)
    private Integer courseId;
    private String courseName;
    private String languageName;
    private String description;
    private Integer level;
    private Integer state;
    @TableLogic(value="0",delval="1")
    //value为正常数据的值，delval为删除数据的值
    private Integer deleted;
}
