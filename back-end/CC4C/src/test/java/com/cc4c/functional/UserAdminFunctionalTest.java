package com.cc4c.functional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.entity.Administrator;
import com.cc4c.entity.Code;
import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.Map;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void registrationRejectsDuplicateNameDuplicateEmailAndInvalidLanguage() throws Exception {
        String name = unique("register_");
        String email = unique("register_") + "@example.com";

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userPayload(name, email, "secret1", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userPayload(name, unique("other_") + "@example.com", "secret1", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.REGISTER_FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userPayload(unique("other_"), email, "secret1", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.REGISTER_FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userPayload(unique("invalid_"), unique("invalid_") + "@example.com", "secret1", 999999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FOREIGN_KEY_CONSTRAINT_VIOLATION.getCode()))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void userLoginProfilePasswordAndCookieLifecycleWorks() throws Exception {
        User user = createUser();

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.LOGIN_FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(cookie().doesNotExist("user_email"));

        MvcResult loginResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(cookie().exists("user_email"))
                .andReturn();
        Cookie loginCookie = loginResult.getResponse().getCookie("user_email");
        assertNotNull(loginCookie);

        mockMvc.perform(get("/users/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/users/verify").cookie(new Cookie("user_email", "forged@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/users/verify").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/users/info").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.name").value(user.getName()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.newPassword").doesNotExist());

        String changedName = unique("changed_");
        mockMvc.perform(put("/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.getId() + "\",\"name\":\"" + changedName + "\",\"major\":1,\"language\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));
        User changed = userDao.selectById(user.getId());
        assertEquals(changedName, changed.getName());
        assertEquals(1, changed.getMajor());
        assertEquals(2, changed.getLanguage());

        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.getId() + "\",\"password\":\"wrong\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + user.getId() + "\",\"password\":\"secret1\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secret2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(put("/users/password/forget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"newPassword\":\"secret2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/users/password/forget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"newPassword\":\"secret3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        MvcResult logout = mockMvc.perform(get("/users/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true))
                .andReturn();
        Cookie cleared = logout.getResponse().getCookie("user_email");
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void unknownUsersReturnFunctionalFailuresInsteadOfServerErrors() throws Exception {
        mockMvc.perform(get("/users/info").cookie(new Cookie("user_email", "missing@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"999999999999\",\"password\":\"old\",\"newPassword\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/users/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"999999999999\",\"name\":\"missing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void emailAndAvatarEndpointsUseIsolatedDependencies() throws Exception {
        String recipient = unique("mail_") + "@example.com";
        when(emailSender.send(anyString(), anyString(), eq(recipient))).thenReturn(true);

        mockMvc.perform(get("/users/email/{email}", recipient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(matchesPattern("\\d{4}")));

        MockMultipartFile avatar = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3, 4});
        mockMvc.perform(multipart("/users/uploadAvatar").file(avatar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.requestPath").value(matchesPattern("http://localhost:5173/test-avatar/img[1-5]/.+avatar\\.png")));
    }

    @Test
    void adminLoginVerificationAndCookieLifecycleWorks() throws Exception {
        Administrator admin = createAdmin();

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + admin.getAdminId() + "\",\"adminPassword\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.LOGIN_FAIL.getCode()))
                .andExpect(cookie().doesNotExist("admin"));

        MvcResult login = mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(cookie().exists("admin"))
                .andReturn();
        Cookie adminCookie = login.getResponse().getCookie("admin");
        assertNotNull(adminCookie);

        mockMvc.perform(get("/admin/verify").cookie(new Cookie("admin", "0000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/admin/verify").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        MvcResult logout = mockMvc.perform(get("/admin/logout"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cleared = logout.getResponse().getCookie("admin");
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
    }

    private Map<String, Object> userPayload(String name, String email, String password, int language) {
        return Map.of(
                "name", name,
                "email", email,
                "password", password,
                "major", 0,
                "language", language);
    }
}
