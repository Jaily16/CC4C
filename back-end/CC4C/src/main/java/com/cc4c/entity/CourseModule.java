package com.cc4c.entity;

import lombok.Data;

import java.util.List;

@Data
public class CourseModule {
    private Integer languageId;
    private Integer priority;
    private String moduleName;
    private Integer level = 0;
    private List<String> courseList;
}
