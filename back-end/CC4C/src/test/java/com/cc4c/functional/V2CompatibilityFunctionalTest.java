package com.cc4c.functional;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
    void userCookieContractRemainsStableAcrossPostLogout() throws Exception {
        UserFixture user = createUser();
        MvcResult login = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("user_email");
        assertNotNull(cookie);
        assertEquals(user.email(), cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(7200, cookie.getMaxAge());

        MvcResult logout = mockMvc.perform(post("/users/logout")).andExpect(status().isOk()).andReturn();
        Cookie cleared = logout.getResponse().getCookie("user_email");
        assertNotNull(cleared);
        assertEquals("/", cleared.getPath());
        assertTrue(cleared.isHttpOnly());
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void administratorCookieContractRemainsStableAcrossPostLogout() throws Exception {
        AdminFixture admin = createAdmin();
        MvcResult login = mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":\"" + admin.id()
                                + "\",\"adminPassword\":\"" + admin.password() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = login.getResponse().getCookie("admin");
        assertNotNull(cookie);
        assertEquals(admin.id(), cookie.getValue());
        assertEquals("/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(3600, cookie.getMaxAge());

        MvcResult logout = mockMvc.perform(post("/admin/logout")).andExpect(status().isOk()).andReturn();
        Cookie cleared = logout.getResponse().getCookie("admin");
        assertNotNull(cleared);
        assertEquals("/", cleared.getPath());
        assertTrue(cleared.isHttpOnly());
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void corsPreflightAllowsExistingFrontendOriginAndCredentials() throws Exception {
        mockMvc.perform(options("/users/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void favoriteSummaryKeepsNamesInsideTheUnifiedPageObject() throws Exception {
        UserFixture user = createUser();
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);

        mockMvc.perform(post("/courses/star/{userId}/{courseId}", user.id(), course.id()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/courses/favorList/{id}", user.id()).param("page", "1").param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseName").value(course.name()))
                .andExpect(jsonPath("$.data.items[0].languageName").value(language.name()))
                .andExpect(jsonPath("$.data.totalPages").value(1));
        mockMvc.perform(post("/courses/star/{userId}/{courseId}", user.id(), course.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void defaultDataSourceIsHikari() {
        assertTrue(dataSource instanceof HikariDataSource);
    }
}
