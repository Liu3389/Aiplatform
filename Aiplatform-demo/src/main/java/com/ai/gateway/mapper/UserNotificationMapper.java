package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserNotification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通知Mapper
 */
@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {
}
