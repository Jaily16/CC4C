package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

@Data
public class Comment {
    @TableId
    private String commentId;
    private Integer userId;
    private String contentPath;
    private String time;
    private Integer like;
    @TableLogic(value="0",delval="1")
    //value为正常数据的值，delval为删除数据的值
    private Integer deleted;
}
