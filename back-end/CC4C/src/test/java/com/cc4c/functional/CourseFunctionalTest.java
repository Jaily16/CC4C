package com.cc4c.functional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.entity.Code;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ProgrammingLanguage;
import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseFunctionalTest extends FunctionalTestSupport {

    @Test
    void moduleAndCoursePublishingFlowWorksAndRejectsInvalidAssociations() throws Exception {
        ProgrammingLanguage language = createLanguage();
        CourseModule module = new CourseModule();
        module.setLanguageId(language.getLanguageId());
        module.setPriority(1);
        module.setModuleName(unique("module_"));
        module.setLevel(0);

        mockMvc.perform(post("/courses/module")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(module)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_ADD_MODULE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/courses/module")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(module)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.MODULE_PRIORITY_REPEATED.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        Course course = coursePayload(language, module, unique("published_"));
        mockMvc.perform(post("/courses/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_ADD_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/courses/{name}", course.getCourseName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_GET_ONE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.courseName").value(course.getCourseName()));

        mockMvc.perform(get("/courses/module/{id}", language.getLanguageId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_GET_MODULES_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].courseList[0]").value(course.getCourseName()));

        mockMvc.perform(post("/courses/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_NAME_REPEATED.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        Course invalid = coursePayload(language, module, unique("invalid_module_"));
        invalid.setPriority(999);
        mockMvc.perform(post("/courses/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_ADD_MODULE_COURSE_FAILED.getCode()))
                .andExpect(jsonPath("$.data").value(false));
        assertFalse(courseDao.exists(new LambdaQueryWrapper<Course>().eq(Course::getCourseName, invalid.getCourseName())));

        mockMvc.perform(get("/courses/module/{id}", 999999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_GET_MODULES_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void courseDiscoveryAndRecommendationEndpointsReturnStableCollections() throws Exception {
        ProgrammingLanguage language = createLanguage();
        CourseModule module = createModule(language);
        Course course = createCourse(language, module);

        mockMvc.perform(get("/courses/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.courseName == '" + course.getCourseName() + "')]").exists());

        mockMvc.perform(get("/courses/search/{info}", course.getCourseName().substring(0, 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_SEARCH_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/courses/language/{name}", language.getLanguageName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_SEARCH_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].courseName").value(course.getCourseName()));

        mockMvc.perform(get("/courses/recommend/{language}/{major}", language.getLanguageId(), 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_GET_RECOMMENDATION_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/courses/search/{info}", unique("missing_")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COURSE_SEARCH_NO_RESULT.getCode()))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void courseFavoriteLifecycleIsIdempotentAndQueryable() throws Exception {
        User user = createUser();
        ProgrammingLanguage language = createLanguage();
        CourseModule module = createModule(language);
        Course course = createCourse(language, module);

        mockMvc.perform(get("/courses/ifFavor/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/courses/star/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/courses/star/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/courses/ifFavor/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/courses/favorList/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseName").value(course.getCourseName()));

        mockMvc.perform(delete("/courses/deleteFavor/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(delete("/courses/deleteFavor/{userId}/{courseId}", user.getId(), course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));
    }

    private Course coursePayload(ProgrammingLanguage language, CourseModule module, String name) {
        Course course = new Course();
        course.setLanguageName(language.getLanguageName());
        course.setLanguageId(language.getLanguageId());
        course.setPriority(module.getPriority());
        course.setCourseName(name);
        course.setDescription("Published from functional test");
        course.setLevel(0);
        course.setState(1);
        return course;
    }
}
