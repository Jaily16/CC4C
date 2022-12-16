package com.cc4c.dao;

import com.cc4c.entity.ProgrammingLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ProgrammingLanguageDaoTest {
    @Autowired
    private ProgrammingLanguageDao programmingLanguageDao;

    @Test
    void testAdd(){
        ProgrammingLanguage programmingLanguage = new ProgrammingLanguage();
        programmingLanguage.setLanguageName("csharp");
        programmingLanguage.setIconPath("G:\\cc4c\\programmingLanguage\\icon\\csharp.png");
        System.out.println(programmingLanguageDao.insert(programmingLanguage));

    }

    @Test
    void testUpdate(){
        ProgrammingLanguage programmingLanguage = new ProgrammingLanguage();
        programmingLanguage.setLanguageId(3);
        programmingLanguage.setIconPath("G:\\cc4c\\programmingLanguage\\icon\\python.png");
        System.out.println(programmingLanguageDao.updateById(programmingLanguage));
    }

}
