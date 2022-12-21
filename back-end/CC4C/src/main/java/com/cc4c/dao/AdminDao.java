package com.cc4c.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.Administrator;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminDao extends BaseMapper<Administrator> {
}
