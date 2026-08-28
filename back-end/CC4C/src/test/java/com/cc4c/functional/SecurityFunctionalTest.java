package com.cc4c.functional;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import com.cc4c.identity.api.Cc4cPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityFunctionalTest extends FunctionalTestSupport {
    private static final String CODE = "654321";

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    @Qualifier("springSessionDefaultRedisSerializer")
    private RedisSerializer<Object> sessionSerializer;

    @Test
    void csrfAndRoleMatrixUseJsonErrorsWithoutRedirects() throws Exception {
        UserFixture user = createUser();
        AdminFixture administrator = createAdmin();

        mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(40300));
        mockMvc.perform(post("/users/login").with(
                        org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mockMvc.perform(post("/courses/module").with(asUser(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users/me").with(asAdministrator(administrator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/blogs/examine").with(asUser(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/not-a-real-route").with(asUser(user)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAwareConcurrencyExpiresTheOldestSession() throws Exception {
        UserFixture user = createUser();
        List<Cookie> userSessions = List.of(
                loginUser(user.email(), user.password()),
                loginUser(user.email(), user.password()),
                loginUser(user.email(), user.password()),
                loginUser(user.email(), user.password()));

        mockMvc.perform(get("/users/me").cookie(userSessions.getFirst()))
                .andExpect(status().isUnauthorized());
        for (Cookie active : userSessions.subList(1, userSessions.size())) {
            mockMvc.perform(get("/users/me").cookie(active)).andExpect(status().isOk());
        }

        AdminFixture administrator = createAdmin();
        Cookie firstAdmin = loginAdministrator(administrator.id(), administrator.password());
        Cookie secondAdmin = loginAdministrator(administrator.id(), administrator.password());
        mockMvc.perform(get("/auth/session").cookie(firstAdmin))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mockMvc.perform(get("/auth/session").cookie(secondAdmin))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void aSecondRepositoryInstanceCanReadTheAuthenticatedRedisSession() throws Exception {
        UserFixture user = createUser();
        Cookie cookie = loginUser(user.email(), user.password());
        String sessionId = new String(
                Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);

        RedisTemplate<String, Object> redisOperations = new RedisTemplate<>();
        redisOperations.setConnectionFactory(redisConnectionFactory);
        redisOperations.setKeySerializer(StringRedisSerializer.UTF_8);
        redisOperations.setHashKeySerializer(StringRedisSerializer.UTF_8);
        redisOperations.setValueSerializer(sessionSerializer);
        redisOperations.setHashValueSerializer(sessionSerializer);
        redisOperations.afterPropertiesSet();
        RedisIndexedSessionRepository secondRepository =
                new RedisIndexedSessionRepository(redisOperations);
        secondRepository.setRedisKeyNamespace(TEST_REDIS_NAMESPACE);
        secondRepository.setDefaultSerializer(sessionSerializer);

        Session restored = secondRepository.findById(sessionId);
        assertNotNull(restored);
        SecurityContext securityContext = restored.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(securityContext);
        assertInstanceOf(
                Cc4cPrincipal.class,
                securityContext.getAuthentication().getPrincipal());
    }

    @Test
    void sessionsHaveRoleSpecificIdleTimeoutsAndSameBrowserIdentityIsReplaced() throws Exception {
        UserFixture user = createUser();
        AdminFixture administrator = createAdmin();

        Cookie userSession = loginUser(user.email(), user.password());
        assertEquals(Duration.ofHours(2), storedSession(userSession).getMaxInactiveInterval());

        MvcResult switched = mockMvc.perform(post("/admin/login")
                        .cookie(userSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + administrator.id()
                                + "\",\"adminPassword\":\"" + administrator.password() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie adminSession = switched.getResponse().getCookie("CC4C_SESSION");
        assertNotNull(adminSession);
        assertEquals(Duration.ofHours(1), storedSession(adminSession).getMaxInactiveInterval());

        mockMvc.perform(get("/auth/session").cookie(userSession))
                .andExpect(jsonPath("$.data.authenticated").value(false));
        mockMvc.perform(get("/auth/session").cookie(adminSession))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void verificationCodesArePurposeBoundSingleUseAndLockedAfterFiveErrors() throws Exception {
        UserFixture first = createUser();
        issueVerificationCode(first.email(), VerificationPurpose.PASSWORD_RESET, CODE);

        mockMvc.perform(post("/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", unique("purpose_"),
                                "email", first.email(),
                                "password", "secret22",
                                "verificationCode", CODE,
                                "major", 0,
                                "language", 1))))
                .andExpect(status().isUnprocessableEntity());
        resetPassword(first.email(), CODE, "secret22", 200);
        resetPassword(first.email(), CODE, "secret33", 422);

        UserFixture second = createUser();
        issueVerificationCode(second.email(), VerificationPurpose.PASSWORD_RESET, CODE);
        for (int attempt = 0; attempt < 5; attempt++) {
            resetPassword(second.email(), "000000", "secret22", 422);
        }
        resetPassword(second.email(), CODE, "secret22", 422);
    }

    @Test
    void verificationEmailResponsesAreGenericAndCooldownReturnsRetryAfter() throws Exception {
        UserFixture existing = createUser();
        String missing = unique("missing_") + "@example.com";
        long ineligibleBefore = countOutboxEvents("identity.verification-email.requested.v1");

        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + existing.email() + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + missing + "\",\"purpose\":\"PASSWORD_RESET\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value(true));
        assertEquals(ineligibleBefore, countOutboxEvents("identity.verification-email.requested.v1"));

        String recipient = unique("limited_") + "@example.com";
        when(verificationCodeGenerator.generate()).thenReturn(CODE);
        long before = countOutboxEvents("identity.verification-email.requested.v1");
        String payload = "{\"email\":\"" + recipient + "\",\"purpose\":\"REGISTER\"}";
        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isAccepted());
        assertEquals(before + 1, countOutboxEvents("identity.verification-email.requested.v1"));
        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.code").value(42900));
    }

    @Test
    void failedLoginCommentAndBlogPublishingLimitsReturn429() throws Exception {
        UserFixture user = createUser();
        String wrongLogin = "{\"email\":\"" + user.email() + "\",\"password\":\"wrong\"}";
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/users/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/users/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(wrongLogin))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")));

        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);
        for (int comment = 0; comment < 10; comment++) {
            mockMvc.perform(post("/comments/course").with(asUser(user)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"limited comment " + comment
                                    + "\",\"courseId\":" + course.id() + "}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/comments/course").with(asUser(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"limited comment 11\",\"courseId\":"
                                + course.id() + "}"))
                .andExpect(status().isTooManyRequests());

        for (int blog = 0; blog < 5; blog++) {
            mockMvc.perform(post("/blogs/submit").with(asUser(user)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", unique("limited_blog_"),
                                    "content", "content",
                                    "languageList", List.of(language.id())))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/blogs/submit").with(asUser(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", unique("limited_blog_"),
                                "content", "content",
                                "languageList", List.of(language.id())))))
                .andExpect(status().isTooManyRequests());
    }

    private Cookie loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("CC4C_SESSION"))
                .andReturn();
        return result.getResponse().getCookie("CC4C_SESSION");
    }

    private Cookie loginAdministrator(String id, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + id + "\",\"adminPassword\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("CC4C_SESSION"))
                .andReturn();
        return result.getResponse().getCookie("CC4C_SESSION");
    }

    private Session storedSession(Cookie cookie) {
        String sessionId = new String(
                Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        Session session = sessions.findById(sessionId);
        assertNotNull(session);
        return session;
    }

    private void resetPassword(String email, String code, String password, int expectedStatus)
            throws Exception {
        mockMvc.perform(put("/users/password/forget").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"verificationCode\":\""
                                + code + "\",\"newPassword\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private long countOutboxEvents(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_outbox WHERE event_type = ?",
                Long.class,
                eventType);
    }
}
