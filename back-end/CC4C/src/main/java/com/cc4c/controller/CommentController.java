package com.cc4c.controller;

import com.cc4c.entity.Code;
import com.cc4c.entity.Comment;
import com.cc4c.entity.Result;
import com.cc4c.service.CommentService;
import com.cc4c.utility.CommentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @PostMapping("/course")
    private Result commentCourse(@RequestBody Comment comment){
        Code code = commentService.comment(comment, CommentType.COURSE_COMMENT.getType());
        if(code != Code.COMMENT_ADD_SUCCESS){
            return new Result(code.getCode(), false, "评论失败");
        }
        return new Result(code.getCode(), true);
    }

    @PostMapping("/blog")
    private Result commentBlog(@RequestBody Comment comment){
        Code code = commentService.comment(comment, CommentType.BLOG_COMMENT.getType());
        if(code != Code.COMMENT_ADD_SUCCESS){
            return new Result(code.getCode(), false, "评论失败");
        }
        return new Result(code.getCode(), true);
    }

    @PostMapping("/indirect")
    private Result commentComment(@RequestBody Comment comment){
        Code code = commentService.comment(comment, CommentType.INDIRECT_COMMENT.getType());
        if(code != Code.COMMENT_ADD_SUCCESS){
            return new Result(code.getCode(), false, "评论失败");
        }
        return new Result(code.getCode(), true);
    }

    @GetMapping("/course/{id}")
    private Result getCourseComments(@PathVariable("id") Integer courseId){
        return commentService.getCourseComments(courseId);
    }

    @GetMapping("/blog/{id}")
    private Result getBlogComments(@PathVariable("id") Long blogId){
        return commentService.getBlogComments(blogId);
    }

}
