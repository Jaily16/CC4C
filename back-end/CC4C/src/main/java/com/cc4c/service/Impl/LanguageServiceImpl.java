package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.dao.LanguageDocumentationDao;
import com.cc4c.dao.ProgrammingLanguageDao;
import com.cc4c.dao.UserSubScribeLanguageDao;
import com.cc4c.entity.Code;
import com.cc4c.entity.LanguageDocumentation;
import com.cc4c.entity.Result;
import com.cc4c.entity.UserSubscribeLanguage;
import com.cc4c.service.LanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class LanguageServiceImpl implements LanguageService {
  @Autowired
  private ProgrammingLanguageDao programmingLanguageDao;

  @Autowired
  private LanguageDocumentationDao languageDocumentationDao;
  @Autowired
  private UserSubScribeLanguageDao userSubScribeLanguageDao;

  public Result document(Integer id) {
    LambdaQueryWrapper<LanguageDocumentation> lambdaQueryWrapper = new LambdaQueryWrapper<LanguageDocumentation>()
        .eq(LanguageDocumentation::getLanguageId, id);
    LanguageDocumentation languageDocumentation = languageDocumentationDao.selectOne(lambdaQueryWrapper);
    if (languageDocumentation == null) {
      return new Result(Code.FAIL.getCode(), 0, "Language Does Not Exist!");
    }
    languageDocumentationDao.updateById(languageDocumentation);
    return new Result(Code.SUCCESS.getCode(), languageDocumentation.getDocLink(), "Add Document Completed!");
  }

  public Result subscribe(Long userId, Integer languageId) {
    LambdaQueryWrapper<UserSubscribeLanguage> lambdaQueryWrapper = new LambdaQueryWrapper<UserSubscribeLanguage>()
        .eq(UserSubscribeLanguage::getUserId, userId)
        .eq(UserSubscribeLanguage::getLanguageId, languageId);
    UserSubscribeLanguage userSubscribeLanguage = userSubScribeLanguageDao.selectOne(lambdaQueryWrapper);
    if (userSubscribeLanguage != null) {
      return new Result(Code.FAIL.getCode(), 0, "Language Already Subscribed!");
    }
    userSubScribeLanguageDao.insert(new UserSubscribeLanguage(userId, languageId, new Date()));
    return new Result(Code.SUCCESS.getCode(), 1, "New Language Subscribed!");
  }

  @Override
  public Result add(LanguageDocumentation languageDocumentation) {
    languageDocumentationDao.insert(languageDocumentation);
    return new Result(Code.SUCCESS.getCode(), 0, "Add a New language!");
  }


}
