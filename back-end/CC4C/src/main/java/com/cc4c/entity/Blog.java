package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class Blog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long blogId;
    private Long writerId;
    private String title;
    private String content;
    private Date publishTime;
    private Integer click;
    private Integer state;
    @TableLogic(value="0",delval="1")
    //value为正常数据的值，delval为删除数据的值
    private Integer deleted;
    @TableField(exist = false)
    private List<Integer> languageList;
}
