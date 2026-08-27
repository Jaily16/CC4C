package com.cc4c.functional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cc4c.identity.internal.EmailSender;
import com.cc4c.identity.internal.VerificationCodeGenerator;
import com.cc4c.identity.internal.VerificationCodeService;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class FunctionalTestSupport {
    protected static final String TEST_REDIS_NAMESPACE =
            "cc4c:test:" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void registerTestDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentVariable("CC4C_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironmentVariable("CC4C_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironmentVariable("CC4C_TEST_DB_PASSWORD"));
        registry.add("spring.data.redis.url", () -> requiredEnvironmentVariable("CC4C_TEST_REDIS_URL"));
        registry.add("spring.session.redis.namespace", () -> TEST_REDIS_NAMESPACE);
        registry.add("cc4c.security.key-prefix", () -> TEST_REDIS_NAMESPACE + ":security");
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

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    protected EmailSender emailSender;

    @MockitoBean
    protected VerificationCodeGenerator verificationCodeGenerator;

    @Autowired
    protected VerificationCodeService verificationCodeService;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected void issueVerificationCode(
            String email, VerificationPurpose purpose, String code) {
        org.mockito.Mockito.when(verificationCodeGenerator.generate()).thenReturn(code);
        org.mockito.Mockito.when(emailSender.send(
                        org.mockito.ArgumentMatchers.eq(code),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(email)))
                .thenReturn(true);
        verificationCodeService.send(email, purpose);
    }

    @AfterEach
    void removeOnlyThisTestNamespaceFromRedis() {
        String sessionPrefix = TEST_REDIS_NAMESPACE + ":sessions:";
        List<String> sessionIds = new ArrayList<>();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
                Cursor<byte[]> cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions()
                                .match(sessionPrefix + "*")
                                .count(100)
                                .build())) {
            cursor.forEachRemaining(key -> {
                if (connection.keyCommands().type(key) == DataType.HASH) {
                    String text = new String(key, StandardCharsets.UTF_8);
                    sessionIds.add(text.substring(sessionPrefix.length()));
                }
            });
        }
        sessionIds.forEach(sessionRepository::deleteById);

        byte[] pattern = (TEST_REDIS_NAMESPACE + "*").getBytes(StandardCharsets.UTF_8);
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
                Cursor<byte[]> cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions()
                                .match(new String(pattern, StandardCharsets.UTF_8))
                                .count(100)
                                .build())) {
            List<byte[]> keys = new ArrayList<>();
            cursor.forEachRemaining(keys::add);
            if (!keys.isEmpty()) {
                connection.keyCommands().del(keys.toArray(byte[][]::new));
            }
        }
    }

    protected UserFixture createUser() {
        long id = IdWorker.getId();
        String name = unique("user_");
        String email = unique("mail_") + "@example.com";
        String encodedPassword = passwordEncoder.encode("secret1");
        jdbcTemplate.update("""
                INSERT INTO user(
                    user_id, user_name, email, password, major, state, create_time,
                    favourite_language, deleted)
                VALUES(?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, 1, 0)
                """, id, name, email, encodedPassword);
        return new UserFixture(id, name, email, "secret1");
    }

    protected AdminFixture createAdmin() {
        String id = Integer.toString(1_000_000 + Math.abs(UUID.randomUUID().hashCode() % 9_000_000));
        String encodedPassword = passwordEncoder.encode("admin123");
        jdbcTemplate.update(
                "INSERT INTO administrator(admin_id, admin_password, deleted) VALUES(?, ?, 0)",
                id,
                encodedPassword);
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

    protected RequestPostProcessor asUser(UserFixture user) {
        Cc4cPrincipal principal = new Cc4cPrincipal(
                AccountRole.USER, Long.toString(user.id()), user.name());
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    protected RequestPostProcessor asAdministrator(AdminFixture administrator) {
        Cc4cPrincipal principal = new Cc4cPrincipal(
                AccountRole.ADMIN, administrator.id(), administrator.id());
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    protected RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
