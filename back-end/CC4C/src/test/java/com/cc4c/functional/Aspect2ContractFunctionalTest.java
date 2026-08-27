package com.cc4c.functional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Aspect2ContractFunctionalTest extends FunctionalTestSupport {

    @Test
    void invalidDtoReturnsFieldErrorsWithoutEchoingTheRequest() throws Exception {
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"x\",\"major\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists())
                .andExpect(jsonPath("$.msg").value("Request validation failed"));
    }

    @Test
    void outOfRangePageIsEmptyButRetainsRealStatistics() throws Exception {
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);

        mockMvc.perform(get("/courses/search/{info}", course.name())
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(true));
    }

    @Test
    void removedGetWriteMethodsAreNotAccepted() throws Exception {
        mockMvc.perform(get("/users/logout")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/users/email/test@example.com")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/courses/star/1/1")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/blogs/collect/1/1")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/blogs/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"1\",\"content\":\"draft\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void openApiDescribesResponseDtosWithoutPasswordFieldsOrEntities() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type")
                        .value("integer"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.data.type")
                        .value("object"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.msg.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/users/logout'].post.responses['400'].content"
                        + "['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/users/register'].post.responses['201'].content")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.newPassword").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserEntity").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BlogEntity").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CourseEntity").doesNotExist());
    }
}
