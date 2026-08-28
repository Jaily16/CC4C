package com.cc4c.functional;

import com.cc4c.identity.IdentityDtos.VerificationPurpose;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminFunctionalTest extends FunctionalTestSupport {
    private static final String CODE = "123456";

    @Test
    void registrationUsesVerificationCodesHashesPasswordsAndPreservesBusinessStatuses() throws Exception {
        String name = unique("register_");
        String email = unique("register_") + "@example.com";
        issueVerificationCode(email, VerificationPurpose.REGISTER, CODE);

        mockMvc.perform(post("/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userPayload(name, email, 1, CODE))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value(true));

        String storedPassword = jdbcTemplate.queryForObject(
                "SELECT password FROM user WHERE email = ?", String.class, email);
        assertNotNull(storedPassword);
        assertTrue(storedPassword.startsWith("{bcrypt}"));

        String duplicateNameEmail = unique("other_") + "@example.com";
        issueVerificationCode(duplicateNameEmail, VerificationPurpose.REGISTER, CODE);
        mockMvc.perform(post("/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                userPayload(name, duplicateNameEmail, 1, CODE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40001));

        String invalidEmail = unique("invalid_") + "@example.com";
        issueVerificationCode(invalidEmail, VerificationPurpose.REGISTER, CODE);
        mockMvc.perform(post("/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                userPayload(unique("invalid_"), invalidEmail, 999999, CODE))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(60001));
    }

    @Test
    void userLoginSessionProfileAndPasswordLifecycleWorks() throws Exception {
        UserFixture user = createUser();

        mockMvc.perform(post("/users/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("账号或密码错误"))
                .andExpect(cookie().doesNotExist("CC4C_SESSION"));

        Cookie session = loginUser(user.email(), user.password());
        mockMvc.perform(get("/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.actorId").value(Long.toString(user.id())));

        mockMvc.perform(get("/users/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.toString(user.id())))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        String changedName = unique("changed_");
        mockMvc.perform(put("/users/me").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + changedName + "\",\"major\":1,\"language\":2}"))
                .andExpect(status().isOk());
        assertEquals(changedName, jdbcTemplate.queryForObject(
                "SELECT user_name FROM user WHERE user_id = ?", String.class, user.id()));

        mockMvc.perform(put("/users/me/password").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"newPassword\":\"secret22\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/users/me/password").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret1\",\"newPassword\":\"secret22\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CC4C_SESSION", 0));

        Cookie secondSession = loginUser(user.email(), "secret22");
        issueVerificationCode(user.email(), VerificationPurpose.PASSWORD_RESET, CODE);
        mockMvc.perform(put("/users/password/forget").cookie(secondSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email()
                                + "\",\"verificationCode\":\"" + CODE
                                + "\",\"newPassword\":\"secret33\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CC4C_SESSION", 0));

        loginUser(user.email(), "secret33");
    }

    @Test
    void anonymousAndForgedLegacyIdentityCannotAccessPrivateUserResources() throws Exception {
        mockMvc.perform(get("/users/me").cookie(new Cookie("user_email", "missing@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("user_email", 0));

        UserFixture missing = new UserFixture(
                999999999999L, "missing", "missing@example.com", "unused");
        mockMvc.perform(get("/users/me").with(asUser(missing)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/users/me").with(asUser(missing)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void verificationEmailIsGenericAndAvatarRequiresAuthenticatedUser() throws Exception {
        String recipient = unique("mail_") + "@example.com";
        when(verificationCodeGenerator.generate()).thenReturn(CODE);
        long before = countOutboxEvents("identity.verification-email.requested.v1");

        mockMvc.perform(post("/users/email").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + recipient + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.verificationCode").doesNotExist());
        assertEquals(before + 1, countOutboxEvents("identity.verification-email.requested.v1"));

        UserFixture user = createUser();
        MockMultipartFile avatar = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3, 4});
        mockMvc.perform(multipart("/users/me/avatar").file(avatar)
                        .with(asUser(user)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestPath")
                        .value(matchesPattern("http://localhost:5173/test-avatar/img[1-5]/.+avatar\\.png")))
                .andExpect(jsonPath("$.data.imgPath").doesNotExist());
    }

    @Test
    void administratorLoginSessionPasswordAndLogoutLifecycleWorks() throws Exception {
        AdminFixture admin = createAdmin();

        mockMvc.perform(post("/admin/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + admin.id() + "\",\"adminPassword\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));

        Cookie session = loginAdministrator(admin.id(), admin.password());
        mockMvc.perform(get("/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.actorId").value(admin.id()));

        mockMvc.perform(put("/admin/password").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"newPassword\":\"admin234\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/admin/password").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin123\",\"newPassword\":\"admin234\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CC4C_SESSION", 0));

        Cookie changedSession = loginAdministrator(admin.id(), "admin234");
        mockMvc.perform(post("/admin/logout").cookie(changedSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("CC4C_SESSION", 0));
    }

    @Test
    void writablePasswordsEnforceBcryptBytesWhileLoginAcceptsLegacyLength() throws Exception {
        String email = unique("bytes_") + "@example.com";
        issueVerificationCode(email, VerificationPurpose.REGISTER, CODE);
        mockMvc.perform(post("/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", unique("bytes_"),
                                "email", email,
                                "password", "密".repeat(25),
                                "verificationCode", CODE,
                                "major", 0,
                                "language", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("密码必须为 8–64 个字符且 UTF-8 编码不超过 72 字节"));

        UserFixture legacy = createUser();
        jdbcTemplate.update(
                "UPDATE user SET password = ? WHERE user_id = ?",
                passwordEncoder.encode("abcd"),
                legacy.id());
        loginUser(legacy.email(), "abcd");
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

    private Map<String, Object> userPayload(
            String name, String email, int language, String verificationCode) {
        return Map.of(
                "name", name,
                "email", email,
                "password", "secret11",
                "verificationCode", verificationCode,
                "major", 0,
                "language", language);
    }

    private long countOutboxEvents(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_outbox WHERE event_type = ?",
                Long.class,
                eventType);
    }
}
