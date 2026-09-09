package com.cc4c.dto;

import lombok.Data;

/** 接收课程模块的语言、序号、名称和级别投影。 */
@Data
public class CourseModuleRow {
    private Integer languageId;
    private Integer priority;
    private String moduleName;
    private Integer level;
}
