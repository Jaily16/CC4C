package com.cc4c.service;

import com.cc4c.entity.Blog;
import com.cc4c.entity.Result;
import com.cc4c.utility.BlogState;
import com.cc4c.utility.OrderType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface BlogService {
  /**
   * 用户收藏博客
   * @param userId 用户id
   * @param blogId 博客id
   * @return 用户收藏博客是否成功的结果
   */
  public Result userCollectsBlog(Long userId, Long blogId);

  /**
   * 用户取消博客收藏
   * @param userId 用户id
   * @param blogId 博客id
   * @return 用户取消博客收藏是否成功的结果
   */
  public Result userCancelBlog(Long userId, Long blogId);

  /**
   * 用户提交博客
   * @param blog 博客
   * @return 用书提交博客是否成功的结果
   */
  public Result userSubmitsBlog(Blog blog);

  /**
   * 获取用户提交的所有博客
   * @param userId 用户id
   * @return 用户提交的所有博客列表结果
   */
  public Result userSubmitsBlogAll(Long userId);

  /**
   * 管理员允许博客发布
   * @param blogId 博客id
   * @return 允许博客发布是否成功的结果
   */
  public Result approveBlog(Long blogId);

  /**
   * 管理员拒绝博客发布
   * @param blogId 博客id
   * @return 拒绝博客发布是否成功的结果
   */
  public Result denyBlog(Long blogId);

  /**
   * 删除博客
   * @param userId 用户id
   * @param blogId 博客id
   * @return 删除博客是否成功的结果
   */
  public Result deleteBlog(Long userId, Long blogId);

  /**
   * 根据需求获取博客列表
   * @param state 博客的状态(已拒绝,未审核,已通过)
   * @param type 排序的类型(按发布时间排序,按点击量排序)
   * @return 根据条件获取到的博客列表结果
   */
  public Result getBlogList(BlogState state, OrderType type);

  /**
   * 将博客与其相关的编程语言进行关联
   * @param blogId 博客id
   * @param languageId 编程语言id
   * @return 博客与编程语言关联是否成功的结果
   */
  public Result involvesLanguage(Long blogId, Integer languageId);

  /**
   * 获取博客详情信息
   * @param id 博客id
   * @return 博客具体内容信息的结果
   */
  public Result blogInfo(Long id);

  public Result blogListByLanguage(Integer languageId);

  /**
   * 添加博客草稿
   * @param userId 用户id
   * @param content 草稿内容
   * @return 添加博客草稿是否成功的结果
   */
  public Result addBlogDraft(Long userId, String content);

  /**
   * 获取博客草稿
   * @param userId 用户id
   * @return 获取的博客草稿
   */
  public Result getBlogDraft(Long userId);

  /**
   * 根据搜索信息搜索博客列表
   * @param query 搜索信息
   * @return 搜索得到的博客列表结果
   */
  public Result searchBlogList(String query);

  /**
   * 获取一篇博客是否已经被某个用户收藏的信息
   * @param userId 用户id
   * @param blogId 博客id
   * @return 该用户是否已经收藏该博客
   */
  public Boolean ifCollect(Long userId, Long blogId);

  /**
   * 获取用户收藏的博客列表
   * @param userId 用户id
   * @return 用户收藏博客列表
   */
  public List<Blog> getCollectBlogList(Long userId);

  /**
   * 增加博客点击量
   * @param blogId 博客id
   * @return 博客点击量增加是否成功的结果
   */
  public Result clickBlog(Long blogId);


}
