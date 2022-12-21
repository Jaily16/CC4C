package com.cc4c.service;

import com.cc4c.entity.Blog;
import com.cc4c.entity.Result;
import org.springframework.stereotype.Service;

@Service
public interface BlogService {
  public Result userCollectsBlog(Long userId, Long blogId);

  public Result userSubmitsBlog(Blog blog);

  public Result approveBlog(Integer blogId);

  public Result unapproveBlog(Integer blogId);

  public Result deleteBlog(Long userId, Long blogId);
  public Result involvesLanguage(Long blogId, Integer languageId);

  public Result blogInfo(Long id);

  public Result blogListByLanguage(Integer languageId);

  /**
   * 添加博客草稿
   * @param userId
   * @param content
   * @return
   */
  public Result addBlogDraft(Long userId, String content);

  /**
   * 获取博客草稿
   * @param userId
   * @return
   */
  public Result getBlogDraft(Long userId);
}
