package com.cc4c.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CourseLevel {
    EASY(-2),
    EASY_AND_DEFAULT(-1),
    DEFAULT(0),
    DIFFICULT_AND_DEFAULT(1),
    DIFFICULT(2),
    MUST_SHOW(66),

    ;
    private final Integer level;
}
