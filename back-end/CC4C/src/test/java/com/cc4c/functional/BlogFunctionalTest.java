package com.cc4c.functional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.dao.BlogInvolvesLanguageDao;
import com.cc4c.entity.Blog;
import com.cc4c.entity.Code;
import com.cc4c.entity.ProgrammingLanguage;
import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlogFunctionalTest extends FunctionalTestSupport {

    @Autowired
    private BlogInvolvesLanguageDao blogInvolvesLanguageDao;

    @Test
    void submissionModerationDiscoveryClickAndDeleteFlowWorks() throws Exception {
        User writer = createUser();
        ProgrammingLanguage language = createLanguage();
        String title = unique("submitted_");
        Blog payload = new Blog();
        payload.setWriterId(writer.getId());
        payload.setTitle(title);
        payload.setContent("Submitted content");
        payload.setLanguageList(List.of(language.getLanguageId()));

        mockMvc.perform(post("/blogs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(1));

        Blog stored = blogDao.selectOne(new LambdaQueryWrapper<Blog>().eq(Blog::getTitle, title));
        assertNotNull(stored);
        assertEquals(0, stored.getState());
        assertEquals(0, stored.getClick());

        mockMvc.perform(get("/blogs/myBlogs/{id}", writer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(title));

        mockMvc.perform(get("/blogs/examine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + title + "')]").exists());

        mockMvc.perform(put("/blogs/approve/{id}", stored.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/blogs/{id}", stored.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Submitted content"));

        mockMvc.perform(get("/blogs/list/{languageId}", language.getLanguageId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(title));

        mockMvc.perform(get("/blogs/search/{info}", title.substring(0, 9)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value(title));

        mockMvc.perform(put("/blogs/click/{id}", stored.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertEquals(1, blogDao.selectById(stored.getBlogId()).getClick());

        mockMvc.perform(delete("/blogs/delete")
                        .param("userId", Long.toString(writer.getId() + 1))
                        .param("blogId", stored.getBlogId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        mockMvc.perform(delete("/blogs/delete")
                        .param("userId", writer.getId().toString())
                        .param("blogId", stored.getBlogId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));

        mockMvc.perform(get("/blogs/{id}", stored.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()));
    }

    @Test
    void publicBlogListsAndDenialFlowWork() throws Exception {
        User writer = createUser();
        Blog published = createBlog(writer, 1);
        Blog pending = createBlog(writer, 0);

        mockMvc.perform(get("/blogs/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[?(@.title == '" + published.getTitle() + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.title == '" + pending.getTitle() + "')]").doesNotExist());

        mockMvc.perform(get("/blogs/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + published.getTitle() + "')]").exists());

        mockMvc.perform(put("/blogs/deny/{id}", pending.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/blogs/{id}", pending.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value(-1));

        mockMvc.perform(get("/blogs/examine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + pending.getTitle() + "')]").doesNotExist());
    }

    @Test
    void blogCollectionLifecycleIsIdempotentAndQueryable() throws Exception {
        User user = createUser();
        Blog blog = createBlog(user, 1);

        mockMvc.perform(get("/blogs/ifCollect/{uid}/{bid}", user.getId(), blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/blogs/collect/{uid}/{bid}", user.getId(), blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/blogs/collect/{uid}/{bid}", user.getId(), blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/blogs/collectList/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].blogId").value(blog.getBlogId().toString()));

        mockMvc.perform(delete("/blogs/collect/{uid}/{bid}", user.getId(), blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS.getCode()));

        mockMvc.perform(delete("/blogs/collect/{uid}/{bid}", user.getId(), blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()));
    }

    @Test
    void draftLifecycleStoresOnceAndConsumesOnRead() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/blogs/draft/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/blogs/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"draft body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/blogs/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"second body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/blogs/draft/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("draft body"));

        mockMvc.perform(get("/blogs/draft/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidBlogReferencesReturnFailuresWithoutPartialWrites() throws Exception {
        User writer = createUser();
        ProgrammingLanguage language = createLanguage();
        String invalidTitle = unique("invalid_language_");
        Blog invalid = new Blog();
        invalid.setWriterId(writer.getId());
        invalid.setTitle(invalidTitle);
        invalid.setContent("invalid");
        invalid.setLanguageList(List.of(999999));

        mockMvc.perform(post("/blogs/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));
        assertFalse(blogDao.exists(new LambdaQueryWrapper<Blog>().eq(Blog::getTitle, invalidTitle)));

        mockMvc.perform(get("/blogs/collect/{uid}/{bid}", writer.getId(), 999999999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(put("/blogs/click/{id}", 999999999999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.FAIL.getCode()))
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(get("/blogs/list/{languageId}", language.getLanguageId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void blogImageUploadReturnsEditorCompatibleResponse() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "article.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/blogs/uploadImg").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("1"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.url").isString());
    }
}
