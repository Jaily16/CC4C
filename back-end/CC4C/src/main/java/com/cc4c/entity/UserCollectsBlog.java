package com.cc4c.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class UserCollectsBlog {
  private Long userId;
  private Long blogId;
  private Date time;
}
