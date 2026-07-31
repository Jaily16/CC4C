package com.cc4c.functional;

import com.cc4c.dao.AdminDao;
import com.cc4c.dao.BlogDao;
import com.cc4c.dao.CourseDao;
import com.cc4c.dao.ProgrammingLanguageDao;
import com.cc4c.dao.UserDao;
import com.cc4c.entity.Administrator;
import com.cc4c.entity.Blog;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ModuleCourse;
import com.cc4c.entity.ProgrammingLanguage;
import com.cc4c.entity.User;
import com.cc4c.utility.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Transactional
abstract class FunctionalTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserDao userDao;

    @Autowired
    protected AdminDao adminDao;

    @Autowired
    protected ProgrammingLanguageDao programmingLanguageDao;

    @Autowired
    protected CourseDao courseDao;

    @Autowired
    protected BlogDao blogDao;

    @MockBean
    protected EmailSender emailSender;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected User createUser() {
        User user = new User();
        user.setName(unique("user_"));
        user.setEmail(unique("mail_") + "@example.com");
        user.setPassword("secret1");
        user.setMajor(0);
        user.setState(0);
        user.setTime(new Date());
        user.setLanguage(1);
        assertEquals(1, userDao.insert(user));
        assertNotNull(user.getId());
        return user;
    }

    protected Administrator createAdmin() {
        Administrator administrator = new Administrator();
        String digits = Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        administrator.setAdminId(digits.substring(0, 7));
        administrator.setAdminPassword("admin123");
        assertEquals(1, adminDao.insert(administrator));
        return administrator;
    }

    protected ProgrammingLanguage createLanguage() {
        ProgrammingLanguage language = new ProgrammingLanguage();
        language.setLanguageName(unique("l"));
        assertEquals(1, programmingLanguageDao.insert(language));
        assertNotNull(language.getLanguageId());
        return language;
    }

    protected CourseModule createModule(ProgrammingLanguage language) {
        CourseModule module = new CourseModule();
        module.setLanguageId(language.getLanguageId());
        module.setPriority(1);
        module.setModuleName(unique("module_"));
        module.setLevel(0);
        assertEquals(1, courseDao.addCourseModule(module));
        return module;
    }

    protected Course createCourse(ProgrammingLanguage language, CourseModule module) {
        Course course = new Course();
        course.setCourseName(unique("course_"));
        course.setLanguageName(language.getLanguageName());
        course.setDescription("Functional test course");
        course.setLevel(0);
        course.setState(1);
        assertEquals(1, courseDao.insert(course));
        assertNotNull(course.getCourseId());

        ModuleCourse relation = new ModuleCourse();
        relation.setLanguageId(language.getLanguageId());
        relation.setPriority(module.getPriority());
        relation.setCourseId(course.getCourseId());
        assertEquals(1, courseDao.addModuleCourse(relation));
        return course;
    }

    protected Blog createBlog(User writer, int state) {
        Blog blog = new Blog();
        blog.setWriterId(writer.getId());
        blog.setTitle(unique("blog_"));
        blog.setContent("Functional test blog");
        blog.setPublishTime(new Date());
        blog.setClick(0);
        blog.setState(state);
        assertEquals(1, blogDao.insert(blog));
        assertNotNull(blog.getBlogId());
        return blog;
    }
}
