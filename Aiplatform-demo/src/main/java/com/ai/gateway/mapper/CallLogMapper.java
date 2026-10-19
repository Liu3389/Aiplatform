package com.ai.gateway.mapper;

import com.ai.gateway.entity.CallLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调用日志Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface CallLogMapper extends BaseMapper<CallLog> {
    
    /**
     * 获取模型使用分布统计
     */
    @Select("SELECT model, COUNT(*) as calls, COALESCE(SUM(cost), 0) as points FROM call_log WHERE status = 1 GROUP BY model ORDER BY calls DESC")
    List<Map<String, Object>> getModelDistribution();
    
    /**
     * 获取热门模型排行
     */
    @Select("SELECT model, COUNT(*) as calls FROM call_log GROUP BY model ORDER BY calls DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopModels(int limit);
    
    /**
     * 统计总cost（SUM聚合）
     */
    @Select("SELECT COALESCE(SUM(cost), 0) FROM call_log WHERE status = 1")
    BigDecimal sumPointsConsumed();
    
    /**
     * 统计指定时间范围内的独立用户数（COUNT DISTINCT）
     * @param startTime 开始时间
     * @param endTime 结束时间（null表示不限制）
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT user_id) FROM call_log WHERE create_time &gt;= #{startTime}" +
            "<if test='endTime != null'> AND create_time &lt; #{endTime}</if>" +
            "</script>")
    Long countDistinctUserId(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计今日调用次数
     */
    @Select("<script>SELECT COUNT(*) FROM call_log WHERE create_time <![CDATA[>=]]> #{startOfDay}</script>")
    Long countTodayCalls(@Param("startOfDay") LocalDateTime startOfDay);
    
    /**
     * 按日期聚合调用数据
     */
    @Select("<script>SELECT DATE(create_time) as date, COUNT(*) as calls, COALESCE(SUM(cost), 0) as points, COALESCE(SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)), 0) as tokens " +
            "FROM call_log WHERE status = 1 AND create_time &gt;= #{startDate} AND create_time &lt; #{endDate} " +
            "GROUP BY DATE(create_time) ORDER BY date</script>")
    List<Map<String, Object>> aggregateByDate(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * 按小时聚合调用数据
     */
    @Select("SELECT HOUR(create_time) as hour, COUNT(*) as calls " +
            "FROM call_log GROUP BY HOUR(create_time) ORDER BY hour")
    List<Map<String, Object>> aggregateByHour();
    
    /**
     * 按月份聚合收入数据
     */
    @Select("<script>SELECT DATE_FORMAT(create_time, '%Y-%m') as month, COALESCE(SUM(cost), 0) as points " +
            "FROM call_log WHERE status = 1 AND create_time &gt;= #{startDate} GROUP BY month ORDER BY month</script>")
    List<Map<String, Object>> aggregateRevenueByMonth(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 按月份聚合用户增长数据
     */
    @Select("<script>SELECT DATE_FORMAT(create_time, '%Y-%m') as month, COUNT(*) as new_users " +
            "FROM user WHERE create_time &gt;= #{startDate} GROUP BY month ORDER BY month</script>")
    List<Map<String, Object>> aggregateUserGrowthByMonth(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 按周聚合数据
     */
    @Select("<script>SELECT YEARWEEK(create_time, 1) as week, COUNT(*) as count " +
            "FROM call_log WHERE create_time &gt;= #{startDate} GROUP BY week ORDER BY week</script>")
    List<Map<String, Object>> aggregateByWeek(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 批量获取用户调用统计
     */
    @Select("<script>" +
            "SELECT user_id, COUNT(*) as calls, COALESCE(SUM(cost), 0) as points " +
            "FROM call_log WHERE status = 1 AND user_id IN " +
            "<foreach item='id' collection='userIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY user_id" +
            "</script>")
    List<Map<String, Object>> getUserCallStats(@Param("userIds") List<Long> userIds);
    
    /**
     * 获取单个用户调用统计
     */
    @Select("SELECT COUNT(*) as calls, COALESCE(SUM(cost), 0) as points FROM call_log WHERE status = 1 AND user_id = #{userId}")
    Map<String, Object> getUserCallStatsSingle(@Param("userId") Long userId);
    
    /**
     * 按日期统计cost
     */
    @Select("<script>SELECT COALESCE(SUM(cost), 0) FROM call_log WHERE status = 1 AND create_time <![CDATA[>=]]> #{startOfDay}</script>")
    BigDecimal sumPointsByDate(@Param("startOfDay") LocalDateTime startOfDay);
    
    /**
     * 获取用户消费排行
     */
    @Select("SELECT u.id as userId, u.username, COUNT(c.id) as calls, COALESCE(SUM(c.cost), 0) as points " +
            "FROM call_log c LEFT JOIN user u ON c.user_id = u.id " +
            "WHERE c.status = 1 GROUP BY c.user_id ORDER BY points DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopUsersByPoints(int limit);
}
