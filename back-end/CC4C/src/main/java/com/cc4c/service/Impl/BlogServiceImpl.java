package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc4c.dao.*;
import com.cc4c.entity.*;
import com.cc4c.service.BlogService;
import com.cc4c.utility.BlogState;
import com.cc4c.utility.OrderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl implements BlogService {
  @Autowired
  private BlogDao blogDao;

  @Autowired
  private UserCollectsBlogDao userCollectsBlogDao;

  @Autowired
  private UserSubmitsBlogDao userSubmitsBlogDao;

  @Autowired
  private BlogInvolvesLanguageDao blogInvolvesLanguageDao;



  @Override
  public Result userCollectsBlog(Long userId, Long blogId) {
    Blog blog = blogDao.selectById(blogId);
    if(!Objects.equals(blog.getState(), BlogState.VERIFIED.getState())){
      return new Result(Code.FAIL.getCode(), false, "您不能收藏尚未发布的博客");
    }
    LambdaQueryWrapper<UserCollectsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserCollectsBlog>()
        .eq(UserCollectsBlog::getUserId, userId)
        .eq(UserCollectsBlog::getBlogId, blogId);
    if (userCollectsBlogDao.selectOne(lambdaQueryWrapper) != null) {
      return new Result(Code.FAIL.getCode(), false, "收藏博客失败!");
    }

    userCollectsBlogDao.insert(new UserCollectsBlog(userId, blogId, new Date()));
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @Override
  public Result userCancelBlog(Long userId, Long blogId) {
    LambdaQueryWrapper<UserCollectsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserCollectsBlog>()
            .eq(UserCollectsBlog::getUserId, userId)
            .eq(UserCollectsBlog::getBlogId, blogId);

    if (!userCollectsBlogDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.FAIL.getCode(), 0, "取消收藏失败");
    }
    userCollectsBlogDao.delete(lambdaQueryWrapper);
    return new Result(Code.SUCCESS.getCode(), 1);
  }

  @Override
  public Result userSubmitsBlog(Blog blog) {
    //先将博客内容存入数据库
    blog.setPublishTime(new Date());
    if(blogDao.insert(blog) <= 0){
      return new Result(Code.FAIL.getCode(), 0, "博客内容提交失败");
    }
    Long blogId = blog.getBlogId();
    Long userId = blog.getWriterId();
    List<Integer> languageList = blog.getLanguageList();
    for(Integer languageId : languageList){
      involvesLanguage(blogId, languageId);
    }
    LambdaQueryWrapper<UserSubmitsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserSubmitsBlog>()
        .eq(UserSubmitsBlog::getUserId, userId)
        .eq(UserSubmitsBlog::getBlogId, blogId);
    if (userSubmitsBlogDao.selectOne(lambdaQueryWrapper) != null) {
      return new Result(Code.FAIL.getCode(), 0, "博客添加失败");
    }
    userSubmitsBlogDao.insert(new UserSubmitsBlog(userId, blogId, new Date()));
    return new Result(Code.SUCCESS.getCode(), 1);
  }

  @Override
  public Result userSubmitsBlogAll(Long userId) {
    LambdaQueryWrapper<UserSubmitsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserSubmitsBlog>()
            .eq(UserSubmitsBlog::getUserId, userId);

    List<UserSubmitsBlog> blogList = userSubmitsBlogDao.selectList(lambdaQueryWrapper);
    List<Long> idList = blogList.stream().map(UserSubmitsBlog::getBlogId).toList();
    List<Blog> list = blogDao.selectBatchIds(idList);
    //将博客按照时间顺序排序
    list = list.stream().sorted(Comparator.comparing(Blog::getPublishTime).reversed())
            .collect(Collectors.toList());
    for (Blog blog : list) {
      blog.setContent(null);
    }

    return new Result(Code.SUCCESS.getCode(), list);
  }

  @Override
  public Result approveBlog(Long blogId) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getBlogId, blogId);
    if (blogDao.selectOne(lambdaQueryWrapper) == null) {
      return new Result(Code.FAIL.getCode(), false, "该博客不存在");
    }

    LambdaUpdateWrapper<Blog> lambdaUpdateWrapper = new LambdaUpdateWrapper<Blog>()
                                                    .eq(Blog::getBlogId, blogId)
                                                    .set(Blog::getState, BlogState.VERIFIED.getState())
                                                    .set(Blog::getPublishTime, new Date());
    blogDao.update(null, lambdaUpdateWrapper);
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @Override
  public Result denyBlog(Long blogId) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getBlogId, blogId);
    if (blogDao.selectOne(lambdaQueryWrapper) == null) {
      return new Result(Code.FAIL.getCode(), 0, "该博客不存在");
    }

    LambdaUpdateWrapper<Blog> lambdaUpdateWrapper = new LambdaUpdateWrapper<Blog>()
                                                        .eq(Blog::getBlogId, blogId)
                                                        .set(Blog::getState, BlogState.DENY.getState());
    blogDao.update(null, lambdaUpdateWrapper);
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @Override
  public Result deleteBlog(Long userId, Long blogId) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>()
        .eq(Blog::getWriterId, userId)
        .eq(Blog::getBlogId, blogId);

    Blog blog = blogDao.selectOne(lambdaQueryWrapper);

    if (blog == null) {
      return new Result(Code.FAIL.getCode(), 0, "Blog Not Exists!");
    }

    blogDao.deleteById(blog);
    return new Result(Code.SUCCESS.getCode(), 1, "Blog Deleted!");
  }

  @Override
  public Result getBlogList(BlogState state, OrderType type) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<>();
    if(type == OrderType.BY_TIME){
      lambdaQueryWrapper.eq(Blog::getState, state.getState())
              .orderByDesc(Blog::getPublishTime);
    }else {
      lambdaQueryWrapper.eq(Blog::getState, state.getState())
              .orderByDesc(Blog::getClick);
    }
    List<Blog> list = blogDao.selectList(lambdaQueryWrapper);
    for (Blog blog : list) {
      blog.setContent(null);
    }
    return new Result(Code.SUCCESS.getCode(), list);
  }


  @Override
  public Result involvesLanguage(Long blogId, Integer languageId) {
    LambdaQueryWrapper<BlogInvolvesLanguage> lambdaQueryWrapper = new LambdaQueryWrapper<BlogInvolvesLanguage>()
        .eq(BlogInvolvesLanguage::getBlogId, blogId)
        .eq(BlogInvolvesLanguage::getLanguageId, languageId);
    if (blogInvolvesLanguageDao.selectOne(lambdaQueryWrapper) != null) {
      return new Result(Code.FAIL.getCode(), 0, "Language Exists!");
    }
    blogInvolvesLanguageDao.insert(new BlogInvolvesLanguage(blogId, languageId));
    return new Result(Code.SUCCESS.getCode(), 1, "Add new language!");
  }

  @Override
  public Result blogInfo(Long id) {
    Blog blog = blogDao.selectById(id);
    if (blog == null) {
      return new Result(Code.FAIL.getCode(), 0, "Blog Not Exists!");
    }
    return new Result(Code.SUCCESS.getCode(), blog, "Blog Get!");
  }

  @Override
  public Result blogListByLanguage(Integer languageId) {
    LambdaQueryWrapper<BlogInvolvesLanguage> lambdaQueryWrapper = new LambdaQueryWrapper<BlogInvolvesLanguage>()
        .eq(BlogInvolvesLanguage::getLanguageId, languageId);
    List<BlogInvolvesLanguage> blogList = blogInvolvesLanguageDao.selectList(lambdaQueryWrapper);
    List<Long> idList = blogList.stream().map(BlogInvolvesLanguage::getBlogId).toList();
    List<Blog> list = blogDao.selectBatchIds(idList);
    List<Blog> showList = new ArrayList<>();
    for(Blog blog : list){
      if(Objects.equals(blog.getState(), BlogState.VERIFIED.getState())){
        blog.setContent(null);
        showList.add(blog);
      }
    }
    return new Result(Code.SUCCESS.getCode(), showList, "Blog List By Language Get!");
  }

  @Override
  public Result addBlogDraft(Long userId, String content) {
    if(blogDao.countDraft(userId)){
      return new Result(Code.FAIL.getCode(), false, "不能重复保存草稿");
    }
    blogDao.addDraft(userId,content);
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @Override
  public Result getBlogDraft(Long userId) {
    String draft = blogDao.getDraft(userId);
    blogDao.deleteDraft(userId);
    return new Result(Code.SUCCESS.getCode(), draft);
  }

  @Override
  public Result searchBlogList(String query) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>()
                                                      .eq(Blog::getState, BlogState.VERIFIED.getState())
                                                      .like(Blog::getTitle, query);

    List<Blog> list = blogDao.selectList(lambdaQueryWrapper);

    return new Result(Code.SUCCESS.getCode(), list, "Blog List Searched!");
  }

  @Override
  public Boolean ifCollect(Long userId, Long blogId) {
    return blogDao.ifCollect(userId, blogId);
  }

  @Override
  public List<Blog> getCollectBlogList(Long userId) {
    return blogDao.getCollectBlogs(userId);
  }

  @Override
  public Result clickBlog(Long blogId) {
    Blog blog = blogDao.selectById(blogId);
    blog.setClick(blog.getClick() + 1);
    blogDao.updateById(blog);
    return new Result(Code.SUCCESS.getCode(), true);
  }

}
