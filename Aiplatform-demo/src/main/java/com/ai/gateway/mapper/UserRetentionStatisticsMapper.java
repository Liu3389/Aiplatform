package com.ai.gateway.mapper;

import com.ai.gateway.entity.UserRetentionStatistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户留存统计表Mapper
 */
@Mapper
public interface UserRetentionStatisticsMapper extends BaseMapper<UserRetentionStatistics> {
}
