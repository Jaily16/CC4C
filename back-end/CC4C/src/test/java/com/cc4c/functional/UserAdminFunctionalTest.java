package com.cc4c.functional;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminFunctionalTest extends FunctionalTestSupport {

    @Test
    void registrationUsesCreatedConflictAndUnprocessableStatuses() throws Exception {
        String name = unique("register_");
        String email = unique("register_") + "@example.com";
        Map<String, Object> payload = userPayload(name, email, 1);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                userPayload(name, unique("other_") + "@example.com", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                userPayload(unique("other_"), email, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                userPayload(unique("invalid_"), unique("invalid_") + "@example.com", 999999))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(60001));
    }

    @Test
    void userLoginProfilePasswordAndCookieLifecycleWorks() throws Exception {
        UserFixture user = createUser();

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(cookie().doesNotExist("user_email"));

        MvcResult login = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("user_email"))
                .andReturn();
        Cookie loginCookie = login.getResponse().getCookie("user_email");
        assertNotNull(loginCookie);

        mockMvc.perform(get("/users/verify"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(false));
        mockMvc.perform(get("/users/verify").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/users/info").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.toString(user.id())))
                .andExpect(jsonPath("$.data.name").value(user.name()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.newPassword").doesNotExist());

        String changedName = unique("changed_");
        mockMvc.perform(put("/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.id()
                                + "\",\"name\":\"" + changedName + "\",\"major\":1,\"language\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertEquals(changedName, jdbcTemplate.queryForObject(
                "SELECT user_name FROM user WHERE user_id = ?", String.class, user.id()));

        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.id()
                                + "\",\"password\":\"wrong\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.id()
                                + "\",\"password\":\"secret1\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/users/password/forget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/users/password/forget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"newPassword\":\"secret3\"}"))
                .andExpect(status().isOk());

        MvcResult logout = mockMvc.perform(post("/users/logout"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, logout.getResponse().getCookie("user_email").getMaxAge());
    }

    @Test
    void unknownUsersUseUnauthorizedAndNotFoundStatuses() throws Exception {
        mockMvc.perform(get("/users/info").cookie(new Cookie("user_email", "missing@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"999999999999\",\"password\":\"old1\",\"newPassword\":\"new1\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"999999999999\",\"name\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emailAndAvatarEndpointsUseIsolatedDependencies() throws Exception {
        String recipient = unique("mail_") + "@example.com";
        when(emailSender.send(anyString(), anyString(), eq(recipient))).thenReturn(true);

        mockMvc.perform(post("/users/email/{email}", recipient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(matchesPattern("\\d{4}")));

        MockMultipartFile avatar = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3, 4});
        mockMvc.perform(multipart("/users/uploadAvatar").file(avatar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestPath")
                        .value(matchesPattern("http://localhost:5173/test-avatar/img[1-5]/.+avatar\\.png")))
                .andExpect(jsonPath("$.data.imgPath").doesNotExist());
    }

    @Test
    void administratorLoginVerificationAndCookieLifecycleWorks() throws Exception {
        AdminFixture admin = createAdmin();

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + admin.id() + "\",\"adminPassword\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("admin"));

        MvcResult login = mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + admin.id()
                                + "\",\"adminPassword\":\"" + admin.password() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("admin"))
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("admin");
        assertNotNull(cookie);

        mockMvc.perform(get("/admin/verify").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        MvcResult logout = mockMvc.perform(post("/admin/logout"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(0, logout.getResponse().getCookie("admin").getMaxAge());
    }

    private Map<String, Object> userPayload(String name, String email, int language) {
        return Map.of(
                "name", name,
                "email", email,
                "password", "secret1",
                "major", 0,
                "language", language);
    }
}
