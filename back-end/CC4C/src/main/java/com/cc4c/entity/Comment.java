package com.cc4c.entity;

import lombok.Data;

@Data
public class Comment {
    private String commentId;
    private Integer userId;
    private String contentPath;
    private String time;
    private Integer like;
}
