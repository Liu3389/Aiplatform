package com.ai.gateway.mapper;

import com.ai.gateway.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户Mapper接口
 * 
 * @author AI Gateway Platform
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET points = #{points}, points_version = points_version + 1 WHERE id = #{userId}")
    int updatePointsAndIncrementVersion(@Param("userId") Long userId, @Param("points") java.math.BigDecimal points);
    
    /**
     * 按天聚合新增用户数
     */
    @Select("SELECT DATE(create_time) as date, COUNT(*) as count FROM user WHERE create_time >= #{startTime} GROUP BY DATE(create_time)")
    List<Map<String, Object>> aggregateNewUsersByDay(LocalDateTime startTime);
}
