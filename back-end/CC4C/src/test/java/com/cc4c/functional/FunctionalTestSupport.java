package com.cc4c.functional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cc4c.identity.internal.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class FunctionalTestSupport {

    @DynamicPropertySource
    static void registerTestDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentVariable("CC4C_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironmentVariable("CC4C_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironmentVariable("CC4C_TEST_DB_PASSWORD"));
    }

    static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required test database environment variable is missing: " + name);
        }
        return value;
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected EmailSender emailSender;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected UserFixture createUser() {
        long id = IdWorker.getId();
        String name = unique("user_");
        String email = unique("mail_") + "@example.com";
        jdbcTemplate.update("""
                INSERT INTO user(
                    user_id, user_name, email, password, major, state, create_time,
                    favourite_language, deleted)
                VALUES(?, ?, ?, 'secret1', 0, 0, CURRENT_TIMESTAMP, 1, 0)
                """, id, name, email);
        return new UserFixture(id, name, email, "secret1");
    }

    protected AdminFixture createAdmin() {
        String id = Integer.toString(1_000_000 + Math.abs(UUID.randomUUID().hashCode() % 9_000_000));
        jdbcTemplate.update(
                "INSERT INTO administrator(admin_id, admin_password, deleted) VALUES(?, 'admin123', 0)", id);
        return new AdminFixture(id, "admin123");
    }

    protected LanguageFixture createLanguage() {
        String name = unique("l");
        jdbcTemplate.update(
                "INSERT INTO programming_language(language_name, deleted) VALUES(?, 0)", name);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT language_id FROM programming_language WHERE language_name = ?", Integer.class, name);
        return new LanguageFixture(id, name);
    }

    protected ModuleFixture createModule(LanguageFixture language) {
        String name = unique("module_");
        jdbcTemplate.update("""
                INSERT INTO course_module(language_id, priority, module_name, level)
                VALUES(?, 1, ?, 0)
                """, language.id(), name);
        return new ModuleFixture(language.id(), 1, name);
    }

    protected CourseFixture createCourse(LanguageFixture language, ModuleFixture module) {
        String name = unique("course_");
        jdbcTemplate.update("""
                INSERT INTO course(language_name, course_name, description, level, state, deleted)
                VALUES(?, ?, 'Functional test course', 0, 1, 0)
                """, language.name(), name);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT course_id FROM course WHERE course_name = ?", Integer.class, name);
        jdbcTemplate.update("""
                INSERT INTO module_course(language_id, priority, course_id)
                VALUES(?, ?, ?)
                """, language.id(), module.priority(), id);
        return new CourseFixture(id, name, language.name());
    }

    protected BlogFixture createBlog(UserFixture writer, int state) {
        long id = IdWorker.getId();
        String title = unique("blog_");
        jdbcTemplate.update("""
                INSERT INTO blog(
                    blog_id, writer_id, title, content, publish_time, click, state, deleted)
                VALUES(?, ?, ?, 'Functional test blog', CURRENT_TIMESTAMP, 0, ?, 0)
                """, id, writer.id(), title, state);
        return new BlogFixture(id, writer.id(), title, state);
    }

    protected record UserFixture(long id, String name, String email, String password) {
    }

    protected record AdminFixture(String id, String password) {
    }

    protected record LanguageFixture(int id, String name) {
    }

    protected record ModuleFixture(int languageId, int priority, String name) {
    }

    protected record CourseFixture(int id, String name, String languageName) {
    }

    protected record BlogFixture(long id, long writerId, String title, int state) {
    }
}
