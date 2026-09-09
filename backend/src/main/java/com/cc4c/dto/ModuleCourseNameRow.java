package com.cc4c.dto;

import lombok.Data;

/** 接收模块序号与课程名称投影，供课程目录按模块归组。 */
@Data
public class ModuleCourseNameRow {
    private Integer priority;
    private String courseName;
}
