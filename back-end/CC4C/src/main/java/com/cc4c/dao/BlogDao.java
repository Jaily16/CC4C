package com.cc4c.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.Blog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogDao extends BaseMapper<Blog> {
}
