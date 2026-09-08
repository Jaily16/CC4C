package com.cc4c.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.AdministratorEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义身份认证的 MyBatis 持久化及结果映射边界。
 */
@Mapper
public interface AdministratorMapper extends BaseMapper<AdministratorEntity> {}
