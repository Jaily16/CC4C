package com.cc4c.functional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlogFunctionalTest extends FunctionalTestSupport {

    @Test
    void submissionModerationAndPagedPublicReadingWork() throws Exception {
        UserFixture writer = createUser();
        LanguageFixture language = createLanguage();
        String title = unique("submitted_");

        mockMvc.perform(post("/blogs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blogPayload(writer.id(), title, language.id()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.blogId").isString());

        Long blogId = jdbcTemplate.queryForObject(
                "SELECT blog_id FROM blog WHERE title = ?", Long.class, title);
        mockMvc.perform(get("/blogs/examine").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].blogId").value(Long.toString(blogId)))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/blogs/approve/{id}", blogId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value(1));

        mockMvc.perform(get("/blogs/{id}", blogId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Functional blog content"))
                .andExpect(jsonPath("$.data.languageList[0]").value(language.id()));

        mockMvc.perform(get("/blogs/all").param("page", "1").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value(title))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist());
    }

    @Test
    void moderationRejectsNonPendingStateTransitions() throws Exception {
        UserFixture writer = createUser();
        BlogFixture pending = createBlog(writer, 0);
        BlogFixture denied = createBlog(writer, 0);

        mockMvc.perform(put("/blogs/approve/{id}", pending.id()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/blogs/approve/{id}", pending.id()))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/blogs/deny/{id}", denied.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value(-1));
        mockMvc.perform(put("/blogs/deny/{id}", denied.id()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void blogFavoritesUsePostConflictAndIndependentPagination() throws Exception {
        UserFixture user = createUser();
        BlogFixture blog = createBlog(user, 1);

        mockMvc.perform(post("/blogs/collect/{uid}/{bid}", user.id(), blog.id()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/blogs/collect/{uid}/{bid}", user.id(), blog.id()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/blogs/ifCollect/{uid}/{bid}", user.id(), blog.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/blogs/collectList/{id}", user.id())
                        .param("page", "1").param("size", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].blogId").value(Long.toString(blog.id())))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(delete("/blogs/collect/{uid}/{bid}", user.id(), blog.id()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/blogs/collect/{uid}/{bid}", user.id(), blog.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftUpsertIsNonDestructiveAndPublishingClearsIt() throws Exception {
        UserFixture user = createUser();
        LanguageFixture language = createLanguage();

        mockMvc.perform(put("/blogs/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.id() + "\",\"content\":\"draft body\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/blogs/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.id() + "\",\"content\":\"updated body\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/blogs/draft/{id}", user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated body"));
        mockMvc.perform(get("/blogs/draft/{id}", user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated body"));

        mockMvc.perform(post("/blogs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                blogPayload(user.id(), unique("draft_publish_"), language.id()))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/blogs/draft/{id}", user.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(delete("/blogs/draft/{id}", user.id()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBlogReferencesNeverCreatePartialWrites() throws Exception {
        UserFixture writer = createUser();
        LanguageFixture language = createLanguage();
        String invalidTitle = unique("invalid_");

        mockMvc.perform(post("/blogs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                blogPayload(writer.id(), invalidTitle, 999999))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blog WHERE title = ?", Integer.class, invalidTitle));

        mockMvc.perform(post("/blogs/collect/{uid}/{bid}", writer.id(), 999999999999L))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/blogs/click/{id}", 999999999999L))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/blogs/list/{languageId}", language.id())
                        .param("page", "2").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2));
    }

    @Test
    void blogImageUploadKeepsEditorStringContract() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "article.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/blogs/uploadImg").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("1"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.url").isString());
    }

    private Map<String, Object> blogPayload(long writerId, String title, int languageId) {
        return Map.of(
                "writerId", Long.toString(writerId),
                "title", title,
                "content", "Functional blog content",
                "languageList", List.of(languageId));
    }
}
