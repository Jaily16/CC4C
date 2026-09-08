package com.cc4c.dto;

import java.util.Date;
import lombok.Data;

/**
 * 承载收藏与评论查询返回的一行投影数据。
 */
@Data
public class CommentRow {
    private Long commentId;
    private Long userId;
    private String content;
    private Date time;
    private Integer like;
    private Long fatherId;
    private Integer layer;
    private String userName;
    private String userAvatar;
    private String fatherName;
}
