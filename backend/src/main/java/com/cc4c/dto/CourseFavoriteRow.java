package com.cc4c.dto;

import lombok.Data;

/**
 * 承载收藏与评论查询返回的一行投影数据。
 */
@Data
public class CourseFavoriteRow {
    private Integer courseId;
    private String courseName;
    private String languageName;
}
