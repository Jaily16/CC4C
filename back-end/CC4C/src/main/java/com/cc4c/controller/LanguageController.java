package com.cc4c.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cc4c.entity.LanguageDocumentation;
import com.cc4c.entity.Result;
import com.cc4c.service.LanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/languages")
public class LanguageController {
  @Autowired
  private LanguageService languageService;

  @PostMapping("/document")
  public Result document(Integer id) {
    System.out.println(id + " document link ...");
    return languageService.document(id);
  }

  @PostMapping("/subscribe")
  public Result subscribe(Long userId, Integer languageId) {
    System.out.println(userId + " subscribes language " + languageId + " ...");
    return languageService.subscribe(userId, languageId);
  }

  @SaCheckRole("admin")
  @PostMapping("/add")
  public Result add(@RequestBody LanguageDocumentation languageDocumentation){
    System.out.println("adding document ...");
    return languageService.add(languageDocumentation);
  }

}
