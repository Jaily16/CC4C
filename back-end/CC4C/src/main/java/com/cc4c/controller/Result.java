package com.cc4c.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    Integer code;
    Object data;
    String msg;

    public Result(Integer code, Object data) {
        this.code = code;
        this.data = data;
    }
}
