package com.cc4c.functional;

import com.cc4c.entity.Administrator;
import com.cc4c.entity.Code;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ProgrammingLanguage;
import com.cc4c.entity.User;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class V2CompatibilityFunctionalTest extends FunctionalTestSupport {

    @Autowired
    private HikariDataSource dataSource;

    @Test
    void userCookieContractRemainsStable() throws Exception {
        User user = createUser();

        MvcResult login = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("user_email");
        assertNotNull(cookie);
        assertEquals(user.getEmail(), cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(7200, cookie.getMaxAge());

        MvcResult logout = mockMvc.perform(get("/users/logout"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cleared = logout.getResponse().getCookie("user_email");
        assertNotNull(cleared);
        assertEquals("/", cleared.getPath());
        assertTrue(cleared.isHttpOnly());
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void administratorCookieContractRemainsStable() throws Exception {
        Administrator administrator = createAdmin();

        MvcResult login = mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(administrator)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("admin");
        assertNotNull(cookie);
        assertEquals(administrator.getAdminId(), cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(3600, cookie.getMaxAge());

        MvcResult logout = mockMvc.perform(get("/admin/logout"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cleared = logout.getResponse().getCookie("admin");
        assertNotNull(cleared);
        assertEquals("/", cleared.getPath());
        assertTrue(cleared.isHttpOnly());
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void corsPreflightAllowsTheExistingFrontendOriginAndCredentials() throws Exception {
        mockMvc.perform(options("/users/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void favoriteSummaryAndBusinessFailureKeepTheirV2Shape() throws Exception {
        User user = createUser();
        ProgrammingLanguage language = createLanguage();
        CourseModule module = createModule(language);
        Course course = createCourse(language, module);

        mockMvc.perform(get("/courses/star/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));

        mockMvc.perform(get("/courses/favorList/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value(course.getCourseName()))
                .andExpect(jsonPath("$.data[0].languageName").value(language.getLanguageName()));

        mockMvc.perform(get("/courses/star/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void defaultDataSourceIsHikari() {
        assertTrue(dataSource instanceof HikariDataSource);
    }
}
