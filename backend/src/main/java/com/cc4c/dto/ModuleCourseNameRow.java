package com.cc4c.dto;

import lombok.Data;

/**
 * 承载课程目录查询返回的一行投影数据。
 */
@Data
public class ModuleCourseNameRow {
    private Integer priority;
    private String courseName;
}
