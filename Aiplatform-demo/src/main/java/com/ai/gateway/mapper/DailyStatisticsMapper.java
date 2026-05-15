package com.ai.gateway.mapper;

import com.ai.gateway.entity.DailyStatistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日统计Mapper
 */
@Mapper
public interface DailyStatisticsMapper extends BaseMapper<DailyStatistics> {
    
    /**
     * 查询日期范围内的统计数据
     */
    List<DailyStatistics> selectByDateRange(@Param("startDate") LocalDate startDate, 
                                            @Param("endDate") LocalDate endDate);
    
    /**
     * 汇总统计（求和）
     */
    Map<String, Object> sumStatistics(@Param("startDate") LocalDate startDate, 
                                      @Param("endDate") LocalDate endDate);
}
