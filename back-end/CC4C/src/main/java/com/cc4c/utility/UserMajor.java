package com.cc4c.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserMajor {
    MAJOR_NOT_IN_CS(-1),
    DEFAULT(0),
    MAJOR_IN_CS(1)
    ;
    private final Integer major;
}
