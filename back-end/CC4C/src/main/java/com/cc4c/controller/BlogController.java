package com.cc4c.controller;

import com.alibaba.fastjson.JSONObject;
import com.cc4c.dao.BlogDao;
import com.cc4c.entity.Blog;
import com.cc4c.entity.BlogDraft;
import com.cc4c.entity.Code;
import com.cc4c.entity.Result;
import com.cc4c.service.BlogService;
import com.cc4c.utility.BlogState;
import com.cc4c.utility.FileUtils;
import com.cc4c.utility.OrderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

  @GetMapping("/home")
  public Result getHomeBlogs(){
    return blogService.getBlogList(BlogState.VERIFIED, OrderType.BY_CLICK);
  }

  @PostMapping("/submit")
  public Result userSubmit(@RequestBody Blog blog) {
    return blogService.userSubmitsBlog(blog);
  }

  @DeleteMapping("/delete")
  public Result delete(Long userId, Long blogId) {
    return blogService.deleteBlog(userId, blogId);
  }

  @GetMapping("/myBlogs/{id}")
  public Result getUserBlogs(@PathVariable(value = "id") Long userId){
    return blogService.userSubmitsBlogAll(userId);
  }

  @GetMapping("/collect/{uid}/{bid}")
  public Result userCollect(@PathVariable(value = "uid") Long userId, @PathVariable(value = "bid") Long blogId) {
    return blogService.userCollectsBlog(userId, blogId);
  }

  @DeleteMapping("/collect/{uid}/{bid}")
  public Result userCancelCollect(@PathVariable(value = "uid") Long userId, @PathVariable(value = "bid") Long blogId){
    return blogService.userCancelBlog(userId, blogId);
  }

  @GetMapping("/collectList/{id}")
  public Result getCollectBlogs(@PathVariable("id") Long userId){
    List<Blog> blogList = blogService.getCollectBlogList(userId);
    return new Result(Code.SUCCESS.getCode(), blogList);
  }

  @GetMapping("/ifCollect/{uid}/{bid}")
  public Result ifCollect(@PathVariable(value = "uid") Long userId, @PathVariable(value = "bid") Long blogId){
    return blogService.ifCollect(userId,blogId) ? new Result(Code.FAIL.getCode(), true) : new Result(Code.FAIL.getCode(), false);
  }

  @PutMapping("/approve/{id}")
  public Result approve(@PathVariable("id") Long blogId) {
    return blogService.approveBlog(blogId);
  }

  @PutMapping("/deny/{id}")
  public Result deny(@PathVariable("id") Long blogId) {
    return blogService.denyBlog(blogId);
  }

  @GetMapping("/{id}")
  public Result blogInfo(@PathVariable("id") Long blogId) {
    return blogService.blogInfo(blogId);
  }

  @GetMapping("/list/{languageId}")
  public Result blogListByLanguage(@PathVariable Integer languageId) {
    return blogService.blogListByLanguage(languageId);
  }

  @GetMapping("/all")
  public Result getAllBlogs(){
    return blogService.getBlogList(BlogState.VERIFIED, OrderType.BY_TIME);
  }

  @GetMapping("/examine")
  public Result getWaitCheckBlogs(){
    return blogService.getBlogList(BlogState.UNVERIFIED, OrderType.BY_TIME);
  }

  @PostMapping("/draft")
  public Result saveDraft(@RequestBody BlogDraft blogDraft){
    return blogService.addBlogDraft(blogDraft.getUserId(),blogDraft.getContent());
  }

  @GetMapping("/draft/{id}")
  public Result getDraft(@PathVariable("id") Long userId){
    return blogService.getBlogDraft(userId);
  }

  @GetMapping("/search/{info}")
  public Result searchBlogs(@PathVariable("info") String info){
    return blogService.searchBlogList(info);
  }

  @PutMapping("/click/{id}")
  public Result clickBlog(@PathVariable("id") Long blogId){
    return blogService.clickBlog(blogId);
  }

}
