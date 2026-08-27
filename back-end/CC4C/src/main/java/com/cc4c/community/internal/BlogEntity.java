package com.cc4c.community.internal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("blog")
class BlogEntity {
    @TableId(value = "blog_id", type = IdType.ASSIGN_ID)
    private Long blogId;
    private Long writerId;
    private String title;
    private String content;
    private Date publishTime;
    private Integer click;
    private Integer state;
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
