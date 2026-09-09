package com.cc4c.dto;

import java.util.Date;
import lombok.Data;

/** 接收已收藏博客的查询投影，包含作者、标题、发布时间、点击数和审核状态。 */
@Data
public class BlogFavoriteRow {
    private Long blogId;
    private Long writerId;
    private String title;
    private Date publishTime;
    private Integer click;
    private Integer state;
}
