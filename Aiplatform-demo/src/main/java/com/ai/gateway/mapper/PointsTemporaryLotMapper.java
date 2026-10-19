package com.ai.gateway.mapper;

import com.ai.gateway.entity.PointsTemporaryLot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PointsTemporaryLotMapper extends BaseMapper<PointsTemporaryLot> {

    @Select("SELECT * FROM points_temporary_lot WHERE user_id = #{userId} AND business_id = #{businessId} LIMIT 1")
    PointsTemporaryLot selectByUserAndBusinessId(@Param("userId") Long userId,
                                                 @Param("businessId") String businessId);

    @Select("SELECT * FROM points_temporary_lot WHERE user_id = #{userId} AND status = 'ACTIVE' "
            + "AND remaining_points > 0 ORDER BY expire_time ASC, id ASC FOR UPDATE")
    List<PointsTemporaryLot> selectActiveForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM points_temporary_lot WHERE user_id = #{userId} AND status = 'ACTIVE' "
            + "AND remaining_points > 0 ORDER BY expire_time ASC, id ASC")
    List<PointsTemporaryLot> selectActiveForUser(@Param("userId") Long userId);

    @Select("SELECT * FROM points_temporary_lot WHERE user_id = #{userId} AND status = 'ACTIVE' "
            + "AND remaining_points > 0 AND expire_time <= #{now} ORDER BY expire_time ASC, id ASC FOR UPDATE")
    List<PointsTemporaryLot> selectExpiredForUpdate(@Param("userId") Long userId,
                                                     @Param("now") LocalDateTime now);

    @Select("SELECT DISTINCT user_id FROM points_temporary_lot WHERE status = 'ACTIVE' "
            + "AND remaining_points > 0 AND expire_time <= #{now} AND user_id > #{afterUserId} "
            + "ORDER BY user_id LIMIT #{limit}")
    List<Long> selectExpiredUserIds(@Param("now") LocalDateTime now,
                                    @Param("afterUserId") Long afterUserId,
                                    @Param("limit") int limit);

    @Select("SELECT id FROM points_temporary_lot WHERE status = 'EXPIRED' AND expired_at >= #{since} "
            + "ORDER BY id LIMIT #{limit}")
    List<Long> selectRecentlyExpiredIds(@Param("since") LocalDateTime since, @Param("limit") int limit);

    @Update("UPDATE points_temporary_lot SET remaining_points = remaining_points - #{amount} "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND remaining_points >= #{amount}")
    int consume(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE points_temporary_lot SET status = 'DEPLETED' "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND remaining_points = 0")
    int markDepleted(@Param("id") Long id);

    @Update("UPDATE points_temporary_lot SET status = 'EXPIRED', remaining_points = 0, expired_at = #{expiredAt} "
            + "WHERE id = #{id} AND status = 'ACTIVE'")
    int markExpired(@Param("id") Long id, @Param("expiredAt") LocalDateTime expiredAt);
}
