package com.cc4c.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CommentFunctionalTest extends FunctionalTestSupport {

    @Test
    void directCourseAndBlogCommentsAreCreatedAndPagedWithUserSummaries() throws Exception {
        UserFixture user = createUser();
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);
        BlogFixture blog = createBlog(user, 1);

        mockMvc.perform(post("/comments/course")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"course comment\",\"courseId\":" + course.id() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(54000))
                .andExpect(jsonPath("$.data.commentId").isString());

        mockMvc.perform(post("/comments/blog")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"blog comment\",\"blogId\":\"" + blog.id() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/comments/course/{id}", course.id())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("course comment"))
                .andExpect(jsonPath("$.data.items[0].userName").value(user.name()))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/comments/blog/{id}", blog.id()).param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("blog comment"));
    }

    @Test
    void repliesDeriveLayerAndStopAfterTwoNestedLevels() throws Exception {
        UserFixture user = createUser();
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);

        postCourseComment(user, course, "root reply target");
        long root = commentId("root reply target");
        postReply(user, root, "first reply", 201);
        long first = commentId("first reply");
        postReply(user, first, "second reply", 201);
        long second = commentId("second reply");
        postReply(user, second, "third reply", 422);
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM comment WHERE content = 'third reply'", Integer.class));

        mockMvc.perform(get("/comments/course/{id}", course.id())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].subCommentList[0].content").value("first reply"))
                .andExpect(
                        jsonPath("$.data.items[0].subCommentList[0].fatherName").value(user.name()))
                .andExpect(jsonPath("$.data.items[0].subCommentList[0].subCommentList[0].content")
                        .value("second reply"));
    }

    @Test
    void invalidReferencesAndBlankContentReturn422And400WithoutOrphans() throws Exception {
        UserFixture user = createUser();

        mockMvc.perform(post("/comments/course")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"bad course\",\"courseId\":999999}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/comments/blog")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"bad blog\",\"blogId\":\"999999999999\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/comments/indirect")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"bad parent\",\"fatherId\":\"999999999999\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/comments/course")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \",\"courseId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").exists());

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM comment
                WHERE content IN ('bad course', 'bad blog', 'bad parent')
                """,
                        Integer.class));
    }

    @Test
    void onlyTheCommentOwnerCanDeleteIt() throws Exception {
        UserFixture owner = createUser();
        UserFixture other = createUser();
        LanguageFixture language = createLanguage();
        ModuleFixture module = createModule(language);
        CourseFixture course = createCourse(language, module);
        postCourseComment(owner, course, "owned comment");
        long commentId = commentId("owned comment");

        mockMvc.perform(delete("/comments/{id}", commentId).with(asUser(other)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/comments/{id}", commentId).with(asUser(owner)).with(csrf()))
                .andExpect(status().isOk());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT deleted FROM comment WHERE comment_id = ?", Integer.class, commentId));
    }

    private void postCourseComment(UserFixture user, CourseFixture course, String content) throws Exception {
        mockMvc.perform(post("/comments/course")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"courseId\":" + course.id() + "}"))
                .andExpect(status().isCreated());
    }

    private void postReply(UserFixture user, long fatherId, String content, int expectedStatus) throws Exception {
        mockMvc.perform(post("/comments/indirect")
                        .with(asUser(user))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"fatherId\":\"" + fatherId + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private long commentId(String content) {
        return jdbcTemplate.queryForObject("SELECT comment_id FROM comment WHERE content = ?", Long.class, content);
    }
}
