package com.cc4c.identity.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义身份认证的 MyBatis 持久化及结果映射边界。
 */
@Mapper
interface UserMapper extends BaseMapper<UserEntity> {}
