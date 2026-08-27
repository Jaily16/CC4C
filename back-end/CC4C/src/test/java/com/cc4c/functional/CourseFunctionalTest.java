package com.cc4c.functional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseFunctionalTest extends FunctionalTestSupport {

    @Test
    void moduleAndCoursePublishingUsesCreatedConflictAndUnprocessableStatuses() throws Exception {
        AdminFixture administrator = createAdmin();
        LanguageFixture language = createLanguage();
        Map<String, Object> module = Map.of(
                "languageId", language.id(),
                "priority", 1,
                "moduleName", unique("module_"),
                "level", 0);

        mockMvc.perform(post("/courses/module")
                        .with(asAdministrator(administrator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(module)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(22001));

        mockMvc.perform(post("/courses/module")
                        .with(asAdministrator(administrator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(module)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(42001));

        String courseName = unique("published_");
        Map<String, Object> course = coursePayload(courseName, language.id(), 1);
        mockMvc.perform(post("/courses/add")
                        .with(asAdministrator(administrator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(22000))
                .andExpect(jsonPath("$.data.courseName").value(courseName));

        mockMvc.perform(post("/courses/add")
                        .with(asAdministrator(administrator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(42000));

        mockMvc.perform(post("/courses/add")
                        .with(asAdministrator(administrator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                coursePayload(unique("invalid_module_"), language.id(), 99))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(52002));
    }

    @Test
    void courseListsAndFavoritesAreDatabasePaged() throws Exception {
        UserFixture user = createUser();
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);

        mockMvc.perform(get("/courses/search/{info}", course.name()).param("page", "1").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseName").value(course.name()))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/courses/language/{name}", language.name())
                        .param("page", "1").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseId").value(course.id()));

        mockMvc.perform(post("/courses/star/{courseId}", course.id())
                        .with(asUser(user)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(post("/courses/star/{courseId}", course.id())
                        .with(asUser(user)).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/courses/star")
                        .with(asUser(user))
                        .param("page", "1").param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseName").value(course.name()))
                .andExpect(jsonPath("$.data.items[0].languageName").value(language.name()))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(delete("/courses/star/{courseId}", course.id())
                        .with(asUser(user)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/courses/star/{courseId}", course.id())
                        .with(asUser(user)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recommendationHierarchyAndPagingBoundsAreValidated() throws Exception {
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);

        mockMvc.perform(get("/courses/recommend/{language}/{major}", language.id(), 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].moduleName").value(module.name()))
                .andExpect(jsonPath("$.data[0].courseList[0]").value(course.name()));

        mockMvc.perform(get("/courses/home").param("page", "0").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/courses/{name}", "definitely-missing-course"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> coursePayload(String name, int languageId, int priority) {
        return Map.of(
                "courseName", name,
                "description", "Functional course",
                "level", 0,
                "state", 1,
                "languageId", languageId,
                "priority", priority);
    }
}
