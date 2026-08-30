package com.cc4c.catalog.internal;

import lombok.Data;

@Data
class CourseModuleRow {
    private Integer languageId;
    private Integer priority;
    private String moduleName;
    private Integer level;
}
