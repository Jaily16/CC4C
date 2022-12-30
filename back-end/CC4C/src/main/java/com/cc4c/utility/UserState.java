package com.cc4c.utility;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserState {
    NORMAL(0)
    ;
    private final Integer state;
}
