package com.ai.gateway.mapper;

import com.ai.gateway.entity.ContentSafetyStatistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容安全统计表Mapper
 */
@Mapper
public interface ContentSafetyStatisticsMapper extends BaseMapper<ContentSafetyStatistics> {
}
