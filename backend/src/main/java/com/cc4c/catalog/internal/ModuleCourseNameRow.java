package com.cc4c.catalog.internal;

import lombok.Data;

/**
 * 承载课程目录查询返回的一行投影数据。
 */
@Data
class ModuleCourseNameRow {
    private Integer priority;
    private String courseName;
}
