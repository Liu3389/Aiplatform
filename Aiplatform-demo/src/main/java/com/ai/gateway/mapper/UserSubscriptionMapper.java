package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserSubscription;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户订阅记录Mapper接口
 */
@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {
}
