package com.cc4c.dto;

import java.util.Date;
import lombok.Data;

/**
 * 承载收藏与评论查询返回的一行投影数据。
 */
@Data
public class BlogFavoriteRow {
    private Long blogId;
    private Long writerId;
    private String title;
    private Date publishTime;
    private Integer click;
    private Integer state;
}
