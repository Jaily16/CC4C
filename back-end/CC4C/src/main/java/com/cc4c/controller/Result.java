package com.cc4c.controller;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result {
  private Integer code;
  private Object data;
  private String msg;
}
