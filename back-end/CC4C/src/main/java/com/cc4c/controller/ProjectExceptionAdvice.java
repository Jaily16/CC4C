package com.cc4c.controller;

import com.cc4c.entity.Result;
import com.cc4c.entity.Code;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProjectExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public Result doException(Exception ex){
        return new Result(Code.FAIL.getCode(), false, "Request processing failed");
    }
}
