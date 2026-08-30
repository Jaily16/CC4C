package com.cc4c.interaction.internal;

import java.util.Date;
import lombok.Data;

@Data
class CommentRow {
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
