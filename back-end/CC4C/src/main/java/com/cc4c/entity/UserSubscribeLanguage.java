package com.cc4c.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class UserSubscribeLanguage {
  private Long userId;
  private Integer languageId;
  private Date subscribeTime;
}
