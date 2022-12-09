package com.cc4c.controller;

public enum Code {
    SUCCESS(100001);
    private final Integer code;

    Code(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
