package com.cc4c.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 映射博客社区数据库记录，字段语义与现有表结构保持一致。
 */
@Data
@TableName("blog")
public class BlogEntity {
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
