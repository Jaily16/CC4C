package com.cc4c.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CourseLevel {
    EASY(-1),
    DEFAULT(0),
    DIFFICULT(1),

    ;
    private final Integer level;
}
