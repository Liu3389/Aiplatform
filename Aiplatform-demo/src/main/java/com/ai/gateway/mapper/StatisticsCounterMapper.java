package com.ai.gateway.mapper;

import com.ai.gateway.entity.StatisticsCounter;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 统计计数器Mapper
 */
@Mapper
public interface StatisticsCounterMapper extends BaseMapper<StatisticsCounter> {
    
    /**
     * 增加计数值（原子操作）
     */
    @Update("UPDATE statistics_counter SET counter_value = counter_value + #{increment} WHERE counter_type = #{counterType}")
    int incrementCounter(@Param("counterType") String counterType, @Param("increment") Long increment);
    
    /**
     * 根据类型查询计数器
     */
    @Select("SELECT * FROM statistics_counter WHERE counter_type = #{counterType}")
    StatisticsCounter selectByType(@Param("counterType") String counterType);
}
