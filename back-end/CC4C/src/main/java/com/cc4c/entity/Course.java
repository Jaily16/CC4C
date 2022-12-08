package com.cc4c.entity;

import lombok.Data;

@Data
public class Course {
    private Integer courseId;
    private String courseName;
    private String languageName;
    private String description;
    private String courseWebsite;
    private String courseBook;
    private String courseVideo;
    private Integer level;
    private Integer state;
}
