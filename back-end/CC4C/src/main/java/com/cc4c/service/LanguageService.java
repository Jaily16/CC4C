package com.cc4c.service;

import com.cc4c.entity.LanguageDocumentation;
import com.cc4c.entity.Result;
import org.springframework.stereotype.Service;

@Service
public interface LanguageService {
  public Result document(Integer id);

  public Result subscribe(Long userId, Integer languageId);

  public Result add(LanguageDocumentation languageDocumentation);
}
