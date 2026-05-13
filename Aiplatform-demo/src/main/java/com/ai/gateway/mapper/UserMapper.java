package com.ai.gateway.mapper;

import com.ai.gateway.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
