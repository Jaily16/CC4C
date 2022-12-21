package com.cc4c.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.alibaba.fastjson.JSONObject;
import com.cc4c.entity.Blog;
import com.cc4c.entity.BlogDraft;
import com.cc4c.entity.Result;
import com.cc4c.service.BlogService;
import com.cc4c.utility.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@RestController
@RequestMapping("/blogs")
public class BlogController {

  @Value("${cc4c.save-img-path}")
  private String saveImgPath;

  @Value("${cc4c.request-img-path}")
  private String requestImgPath;
  @Autowired
  private BlogService blogService;

  @PostMapping("/uploadImg")
  public JSONObject img(@RequestParam(value = "file") MultipartFile file){
    JSONObject json = new JSONObject();
    try{
      String s = FileUtils.uploadImg(file,
              saveImgPath,
              Objects.requireNonNull(file.getOriginalFilename()),
              requestImgPath);
      System.out.println(s);
      json.put("success", "1");
      json.put("message", "success");
      json.put("url", s);
      return json;
    }catch (Exception e){
      e.printStackTrace();
      json.put("STATUS", "ERROR");
      json.put("MSG", "上传图片格式非法");
      return json;
    }
  }


  @PostMapping("/submit")
  public Result userSubmit(@RequestBody Blog blog) {
    return blogService.userSubmitsBlog(blog);
  }

  @PostMapping("/delete")
  public Result delete(Long userId, Long blogId) {
    return blogService.deleteBlog(userId, blogId);
  }

  @PostMapping("/collect")
  public Result userCollect(Long userId, Long blogId) {
    return blogService.userCollectsBlog(userId, blogId);
  }

  @SaCheckRole("admin")
  @PutMapping("/approve")
  public Result approve(Integer blogId) {
    return blogService.approveBlog(blogId);
  }

  @SaCheckRole("admin")
  @PutMapping("/unapprove")
  public Result unapprove(Integer blogId) {
    return blogService.unapproveBlog(blogId);
  }

  @PostMapping("/language")
  public Result language(Long blogId, Integer languageId) {
    return blogService.involvesLanguage(blogId, languageId);
  }

  @GetMapping("/{blog_id}")
  public Result blogInfo(@PathVariable Long blog_id) {
    return blogService.blogInfo(blog_id);
  }

  @GetMapping("/list")
  public Result blogListByLanguage(Integer languageId) {
    return blogService.blogListByLanguage(languageId);
  }

  @PostMapping("/draft")
  public Result saveDraft(@RequestBody BlogDraft blogDraft){
    return blogService.addBlogDraft(blogDraft.getUserId(),blogDraft.getContent());
  }

  @GetMapping("/draft/{id}")
  public Result getDraft(@PathVariable("id") Long userId){
    return blogService.getBlogDraft(userId);
  }

}
