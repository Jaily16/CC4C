package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class Blog {
    @TableId(type = IdType.ASSIGN_ID)
    private String blogId;
    private Integer writerId;
    private String title;
    private String contentPath;
    @JsonFormat(timezone = "UTC+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;
    private Integer click;
    private Integer state;
    @TableLogic(value="0",delval="1")
    //value为正常数据的值，delval为删除数据的值
    private Integer deleted;
}
