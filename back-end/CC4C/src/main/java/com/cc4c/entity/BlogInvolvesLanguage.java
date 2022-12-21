package com.cc4c.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BlogInvolvesLanguage {
  private Long blogId;
  private Integer languageId;
}
