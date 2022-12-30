package com.cc4c.service;

import com.cc4c.entity.Blog;
import com.cc4c.entity.Result;
import com.cc4c.utility.BlogState;
import com.cc4c.utility.OrderType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface BlogService {
  public Result userCollectsBlog(Long userId, Long blogId);

  public Result userCancelBlog(Long userId, Long blogId);

  public Result userSubmitsBlog(Blog blog);

  public Result userSubmitsBlogAll(Long userId);

  public Result approveBlog(Long blogId);

  public Result denyBlog(Long blogId);

  public Result deleteBlog(Long userId, Long blogId);

  public Result getBlogList(BlogState state, OrderType type);

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

  public Result searchBlogList(String query);

  public Boolean ifCollect(Long userId, Long blogId);

  public List<Blog> getCollectBlogList(Long userId);

  public Result clickBlog(Long blogId);


}
