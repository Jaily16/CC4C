package com.cc4c.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result {
  private Integer code;
  private Object data;
  private String msg;

  public Result(Integer code, Object data) {
    this.code = code;
    this.data = data;
  }
}
