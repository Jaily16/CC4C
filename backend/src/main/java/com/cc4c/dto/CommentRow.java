package com.cc4c.dto;

import java.util.Date;
import lombok.Data;

/** 接收评论与作者、父评论作者的联合查询投影，供评论树组装。 */
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
