package com.cc4c.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdministratorAllowsBlog {
  private String adminId;
  private Long blogId;
}
