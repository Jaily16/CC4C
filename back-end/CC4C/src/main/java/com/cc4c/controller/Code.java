package com.cc4c.controller;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Code {
  SUCCESS(200),
  REGISTER_FAIL(40001),
//  USERNAME_EXISTS(40001),
//  USERNAME_EXISTS(40001),
//  USERNAME_EXISTS(40001),
//  USERNAME_EXISTS(40001),
//  USERNAME_EXISTS(40001),
  LOGIN_FAIL(40002);
  private final Integer code;
}
