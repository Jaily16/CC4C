package com.cc4c.dto;

import lombok.Data;

/** 接收用户已收藏课程的标识、名称和语言名称投影。 */
@Data
public class CourseFavoriteRow {
    private Integer courseId;
    private String courseName;
    private String languageName;
}
