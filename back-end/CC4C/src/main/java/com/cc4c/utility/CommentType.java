package com.cc4c.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentType {
    COURSE_COMMENT(1),
    BLOG_COMMENT(2),
    INDIRECT_COMMENT(3);
    private final Integer type;
}
