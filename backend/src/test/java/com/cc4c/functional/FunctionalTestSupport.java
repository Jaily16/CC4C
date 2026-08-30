package com.cc4c.functional;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import com.cc4c.identity.internal.VerificationCodeGenerator;
import com.cc4c.identity.internal.VerificationCodeService;
import com.cc4c.shared.BusinessCache;
import com.cc4c.shared.MessagingTopology;
import com.cc4c.support.Cc4cTestInfrastructure;
import com.cc4c.support.RabbitTestResources;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(Cc4cTestInfrastructure.class)
public abstract class FunctionalTestSupport {
    protected static final String TEST_REDIS_NAMESPACE =
            "cc4c:test:" + UUID.randomUUID().toString().replace("-", "");
    protected static final String TEST_RABBIT_NAMESPACE =
            "cc4c.test.messaging." + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void registerTestDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", Cc4cTestInfrastructure::securityRedisUrl);
        registry.add("cc4c.cache.redis-url", Cc4cTestInfrastructure::cacheRedisUrl);
        registry.add("cc4c.cache.namespace", () -> TEST_REDIS_NAMESPACE + ":cache");
        registry.add("spring.session.redis.namespace", () -> TEST_REDIS_NAMESPACE);
        registry.add("cc4c.security.key-prefix", () -> TEST_REDIS_NAMESPACE + ":security");
        Cc4cTestInfrastructure.registerRabbitProperties(registry);
        registry.add("cc4c.messaging.namespace", () -> TEST_RABBIT_NAMESPACE);
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
    protected BusinessCache businessCache;

    @Autowired
    private AmqpAdmin rabbitAdmin;

    @Autowired
    private MessagingTopology messagingTopology;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    protected VerificationCodeGenerator verificationCodeGenerator;

    @MockitoBean
    protected JavaMailSender javaMailSender;

    @Autowired
    protected VerificationCodeService verificationCodeService;

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected void issueVerificationCode(String email, VerificationPurpose purpose, String code) {
        Instant issuedAt = Instant.now();
        boolean activated = verificationCodeService.activateForDelivery(
                email, purpose, code, UUID.randomUUID().toString(), issuedAt, issuedAt.plus(Duration.ofMinutes(10)));
        if (!activated) {
            throw new IllegalStateException("Unable to activate functional-test verification code");
        }
    }

    @AfterEach
    void removeOnlyThisTestNamespaceFromRedis() {
        businessCache.clearNamespaceForTests();
        String sessionPrefix = TEST_REDIS_NAMESPACE + ":sessions:";
        List<String> sessionIds = new ArrayList<>();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
                Cursor<byte[]> cursor = connection
                        .keyCommands()
                        .scan(ScanOptions.scanOptions()
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
                Cursor<byte[]> cursor = connection
                        .keyCommands()
                        .scan(ScanOptions.scanOptions()
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

    @AfterAll
    void removeOnlyThisTestNamespaceFromRabbit() {
        RabbitTestResources.deleteKnownNamespaceResources(rabbitAdmin, messagingTopology);
    }

    protected UserFixture createUser() {
        long id = IdWorker.getId();
        String name = unique("user_");
        String email = unique("mail_") + "@example.com";
        String encodedPassword = passwordEncoder.encode("secret1");
        jdbcTemplate.update(
                """
                INSERT INTO user(
                    user_id, user_name, email, password, major, state, create_time,
                    favourite_language, deleted)
                VALUES(?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, 1, 0)
                """,
                id,
                name,
                email,
                encodedPassword);
        return new UserFixture(id, name, email, "secret1");
    }

    protected AdminFixture createAdmin() {
        String id = Integer.toString(1_000_000 + Math.abs(UUID.randomUUID().hashCode() % 9_000_000));
        String encodedPassword = passwordEncoder.encode("admin123");
        jdbcTemplate.update(
                "INSERT INTO administrator(admin_id, admin_password, deleted) VALUES(?, ?, 0)", id, encodedPassword);
        return new AdminFixture(id, "admin123");
    }

    protected LanguageFixture createLanguage() {
        String name = unique("l");
        jdbcTemplate.update("INSERT INTO programming_language(language_name, deleted) VALUES(?, 0)", name);
        Integer id = jdbcTemplate.queryForObject(
                "SELECT language_id FROM programming_language WHERE language_name = ?", Integer.class, name);
        return new LanguageFixture(id, name);
    }

    protected ModuleFixture createModule(LanguageFixture language) {
        String name = unique("module_");
        jdbcTemplate.update(
                """
                INSERT INTO course_module(language_id, priority, module_name, level)
                VALUES(?, 1, ?, 0)
                """,
                language.id(),
                name);
        return new ModuleFixture(language.id(), 1, name);
    }

    protected CourseFixture createCourse(LanguageFixture language, ModuleFixture module) {
        String name = unique("course_");
        jdbcTemplate.update(
                """
                INSERT INTO course(language_name, course_name, description, level, state, deleted)
                VALUES(?, ?, 'Functional test course', 0, 1, 0)
                """,
                language.name(),
                name);
        Integer id =
                jdbcTemplate.queryForObject("SELECT course_id FROM course WHERE course_name = ?", Integer.class, name);
        jdbcTemplate.update(
                """
                INSERT INTO module_course(language_id, priority, course_id)
                VALUES(?, ?, ?)
                """,
                language.id(),
                module.priority(),
                id);
        return new CourseFixture(id, name, language.name());
    }

    protected BlogFixture createBlog(UserFixture writer, int state) {
        long id = IdWorker.getId();
        String title = unique("blog_");
        jdbcTemplate.update(
                """
                INSERT INTO blog(
                    blog_id, writer_id, title, content, publish_time, click, state, deleted)
                VALUES(?, ?, ?, 'Functional test blog', CURRENT_TIMESTAMP, 0, ?, 0)
                """,
                id,
                writer.id(),
                title,
                state);
        return new BlogFixture(id, writer.id(), title, state);
    }

    protected record UserFixture(long id, String name, String email, String password) {}

    protected record AdminFixture(String id, String password) {}

    protected record LanguageFixture(int id, String name) {}

    protected record ModuleFixture(int languageId, int priority, String name) {}

    protected record CourseFixture(int id, String name, String languageName) {}

    protected record BlogFixture(long id, long writerId, String title, int state) {}

    protected RequestPostProcessor asUser(UserFixture user) {
        Cc4cPrincipal principal = new Cc4cPrincipal(AccountRole.USER, Long.toString(user.id()), user.name());
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    protected RequestPostProcessor asAdministrator(AdminFixture administrator) {
        Cc4cPrincipal principal = new Cc4cPrincipal(AccountRole.ADMIN, administrator.id(), administrator.id());
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    protected RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
