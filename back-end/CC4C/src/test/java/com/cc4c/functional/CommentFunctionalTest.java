package com.cc4c.functional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cc4c.dao.CommentDao;
import com.cc4c.entity.Blog;
import com.cc4c.entity.Code;
import com.cc4c.entity.Comment;
import com.cc4c.entity.Course;
import com.cc4c.entity.CourseModule;
import com.cc4c.entity.ProgrammingLanguage;
import com.cc4c.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentFunctionalTest extends FunctionalTestSupport {

    @Autowired
    private CommentDao commentDao;

    @Test
    void directCourseAndBlogCommentsAreCreatedAndEnriched() throws Exception {
        User user = createUser();
        ProgrammingLanguage language = createLanguage();
        CourseModule module = createModule(language);
        Course course = createCourse(language, module);
        Blog blog = createBlog(user, 1);

        mockMvc.perform(post("/comments/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"course comment\",\"courseId\":" + course.getCourseId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COMMENT_ADD_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/comments/blog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"blog comment\",\"blogId\":\"" + blog.getBlogId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COMMENT_ADD_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/comments/course/{id}", course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.COMMENT_GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].content").value("course comment"))
                .andExpect(jsonPath("$.data[0].userName").value(user.getName()));

        mockMvc.perform(get("/comments/blog/{id}", blog.getBlogId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("blog comment"))
                .andExpect(jsonPath("$.data[0].userName").value(user.getName()));
    }

    @Test
    void repliesDeriveTheirLayerAndStopAfterTwoNestedLevels() throws Exception {
        User user = createUser();
        ProgrammingLanguage language = createLanguage();
        CourseModule module = createModule(language);
        Course course = createCourse(language, module);

        postCourseComment(user, course, "root reply target");
        Comment root = commentByContent("root reply target");

        postReply(user, root.getCommentId(), "first reply", true);
        Comment first = commentByContent("first reply");

        postReply(user, first.getCommentId(), "second reply", true);
        Comment second = commentByContent("second reply");

        postReply(user, second.getCommentId(), "third reply", false);
        assertFalse(commentDao.exists(new LambdaQueryWrapper<Comment>().eq(Comment::getContent, "third reply")));

        mockMvc.perform(get("/comments/course/{id}", course.getCourseId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subCommentList[0].content").value("first reply"))
                .andExpect(jsonPath("$.data[0].subCommentList[0].fatherName").value(user.getName()))
                .andExpect(jsonPath("$.data[0].subCommentList[0].subCommentList[0].content").value("second reply"));
    }

    @Test
    void invalidCommentReferencesDoNotLeaveOrphanComments() throws Exception {
        User user = createUser();

        mockMvc.perform(post("/comments/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"bad course\",\"courseId\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/comments/blog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"bad blog\",\"blogId\":\"999999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/comments/indirect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"bad parent\",\"fatherId\":\"999999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        mockMvc.perform(post("/comments/indirect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"999999999999\",\"content\":\"bad user\",\"fatherId\":\"999999999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        assertFalse(commentDao.exists(new LambdaQueryWrapper<Comment>()
                .in(Comment::getContent, "bad course", "bad blog", "bad parent", "bad user")));
    }

    private void postCourseComment(User user, Course course, String content) throws Exception {
        mockMvc.perform(post("/comments/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"" + content + "\",\"courseId\":" + course.getCourseId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    private void postReply(User user, Long fatherId, String content, boolean success) throws Exception {
        mockMvc.perform(post("/comments/indirect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getId() + "\",\"content\":\"" + content + "\",\"fatherId\":\"" + fatherId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(success));
    }

    private Comment commentByContent(String content) {
        Comment comment = commentDao.selectOne(new LambdaQueryWrapper<Comment>().eq(Comment::getContent, content));
        assertNotNull(comment);
        return comment;
    }
}
