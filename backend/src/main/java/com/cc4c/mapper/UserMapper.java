package com.cc4c.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cc4c.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/** 通过 MyBatis-Plus 访问用户记录，继承基础 CRUD 和逻辑删除能力。 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {}
