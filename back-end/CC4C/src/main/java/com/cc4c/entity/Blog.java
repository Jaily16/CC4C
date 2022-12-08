package com.cc4c.entity;

import lombok.Data;

@Data
public class Blog {
    private String blogId;
    private Integer writerId;
    private String title;
    private String contentPath;
    private String publishTime;
    private Integer click;
    private Integer state;
}
