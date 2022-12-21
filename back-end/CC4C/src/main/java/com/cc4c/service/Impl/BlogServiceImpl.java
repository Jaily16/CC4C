package com.cc4c.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cc4c.dao.*;
import com.cc4c.entity.*;
import com.cc4c.service.BlogService;
import com.cc4c.utility.BlogState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class BlogServiceImpl implements BlogService {
  @Autowired
  private BlogDao blogDao;

  @Autowired
  private UserDao userDao;

  @Autowired
  private UserCollectsBlogDao userCollectsBlogDao;

  @Autowired
  private UserSubmitsBlogDao userSubmitsBlogDao;

  @Autowired
  private BlogInvolvesLanguageDao blogInvolvesLanguageDao;

//  public Result selectBlogListByUserId(Long id) {
//    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getWriterId, id);
//    List<Blog> blogList = blogDao.selectList(lambdaQueryWrapper);
//    return new Result(Code.SUCCESS.getCode(), blogList, "Get List By user_id!");
//  }
//
//  public Result selectBlogListUnVerified() {
//    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getState, 0);
//    List<Blog> blogList = blogDao.selectList(lambdaQueryWrapper);
//    return new Result(Code.SUCCESS.getCode(), blogList, "Get List Unverified!");
//  }

  public Result addViewCount() {
//    TODO
    return null;
  }

  @Override
  public Result userCollectsBlog(Long userId, Long blogId) {
    LambdaQueryWrapper<UserCollectsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserCollectsBlog>()
        .eq(UserCollectsBlog::getUserId, userId)
        .eq(UserCollectsBlog::getBlogId, blogId);
    if (userCollectsBlogDao.selectOne(lambdaQueryWrapper) != null) {
      return new Result(Code.FAIL.getCode(), 0, "Insertion Failed!");
    }

    userCollectsBlogDao.insert(new UserCollectsBlog(userId, blogId, new Date()));
    return new Result(Code.SUCCESS.getCode(), 1, "User Collects Blog!");
  }

  @Override
  public Result userCancelBlog(Long userId, Long blogId) {
    LambdaQueryWrapper<UserCollectsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserCollectsBlog>()
            .eq(UserCollectsBlog::getUserId, userId)
            .eq(UserCollectsBlog::getBlogId, blogId);

    if (!userCollectsBlogDao.exists(lambdaQueryWrapper)) {
      return new Result(Code.FAIL.getCode(), 0, "User Not Collect!");
    }
    return new Result(Code.SUCCESS.getCode(), 1, "User Cancel Blog!");
  }

  @Override
  public Result userSubmitsBlog(Blog blog) {
    //先将博客内容存入数据库
    blog.setState(BlogState.VERIFIED.getState());
    if(blogDao.insert(blog) <= 0){
      return new Result(Code.FAIL.getCode(), 0, "Blog insert Failed!");
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
      return new Result(Code.FAIL.getCode(), 0, "Insertion Failed!");
    }
    userSubmitsBlogDao.insert(new UserSubmitsBlog(userId, blogId, new Date()));
    return new Result(Code.SUCCESS.getCode(), 1, "User Submits Blog!");
  }

  @Override
  public Result userSubmitsBlogAll(Long userId) {
    LambdaQueryWrapper<UserSubmitsBlog> lambdaQueryWrapper = new LambdaQueryWrapper<UserSubmitsBlog>()
            .eq(UserSubmitsBlog::getUserId, userId);
    if (userDao.selectById(userId) == null) {
      return new Result(Code.FAIL.getCode(), 0, "User Not Exists!");
    }

    List<UserSubmitsBlog> blogList = userSubmitsBlogDao.selectList(lambdaQueryWrapper);

    List<Long> idList = blogList.stream().map(UserSubmitsBlog::getBlogId).toList();

    List<Blog> list = blogDao.selectBatchIds(idList);

    for (Blog blog : list) {
      blog.setContent(null);
    }

    return new Result(Code.SUCCESS.getCode(), 1, "User Submits Blog!");
  }

  @Override
  public Result approveBlog(Integer blogId) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getBlogId, blogId);
    if (blogDao.selectOne(lambdaQueryWrapper) == null) {
      return new Result(Code.FAIL.getCode(), 0, "Error Blog Id!");
    }

    LambdaUpdateWrapper<Blog> lambdaUpdateWrapper = new LambdaUpdateWrapper<Blog>().set(Blog::getState, 1);
    blogDao.update(null, lambdaUpdateWrapper);
    return new Result(Code.SUCCESS.getCode(), 1, "Blog Approved!");
  }

  @Override
  public Result unapproveBlog(Integer blogId) {
    LambdaQueryWrapper<Blog> lambdaQueryWrapper = new LambdaQueryWrapper<Blog>().eq(Blog::getBlogId, blogId);
    if (blogDao.selectOne(lambdaQueryWrapper) == null) {
      return new Result(Code.FAIL.getCode(), 0, "Error Blog Id!");
    }

    LambdaUpdateWrapper<Blog> lambdaUpdateWrapper = new LambdaUpdateWrapper<Blog>().set(Blog::getState, 0);
    blogDao.update(null, lambdaUpdateWrapper);
    return new Result(Code.SUCCESS.getCode(), 1, "Blog Unapproved!");
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
  public Result blogListAll() {
    List<Blog> list = blogDao.selectList(null);

    for (Blog blog : list) {
      blog.setContent(null);
    }
    return new Result(Code.SUCCESS.getCode(), list, "Blog List All Get!");
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

    return new Result(Code.SUCCESS.getCode(), list, "Blog List By Language Get!");
  }

  @Override
  public Result addBlogDraft(Long userId, String content) {
    blogDao.addDraft(userId,content);
    return new Result(Code.SUCCESS.getCode(), true);
  }

  @Override
  public Result getBlogDraft(Long userId) {
    String draft = blogDao.getDraft(userId);
    blogDao.deleteDraft(userId);
    return new Result(Code.SUCCESS.getCode(), draft);
  }


}
