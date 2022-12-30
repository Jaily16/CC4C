package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class Comment {
    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commentId;
    private Long userId;
    private String content;
    private Date time;
    private Integer like;
    @TableLogic(value="0",delval="1")
    //value为正常数据的值，delval为删除数据的值
    private Integer deleted;
    @TableField(exist = false)
    private Integer courseId;
    @TableField(exist = false)
    private Long blogId;
    @TableField(exist = false)
    private Long fatherId;
    @TableField(exist = false)
    private Integer layer = 0;
    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String fatherName;
    @TableField(exist = false)
    private List<Comment> subCommentList;
}
