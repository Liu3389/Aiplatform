package com.ai.gateway.mapper;

import com.ai.gateway.entity.AiChargeIntent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiChargeIntentMapper extends BaseMapper<AiChargeIntent> {

    @Insert("INSERT IGNORE INTO ai_charge_intent (request_id, request_fingerprint, user_id, reserved_points, "
            + "actual_points, model_name, status, reservation_confirmed, create_time, update_time) "
            + "VALUES (#{requestId}, #{requestFingerprint}, #{userId}, #{reservedPoints}, #{actualPoints}, "
            + "#{modelName}, #{status}, #{reservationConfirmed}, #{createTime}, #{updateTime})")
    int insertIfAbsent(AiChargeIntent intent);

    @Select("SELECT * FROM ai_charge_intent WHERE request_id = #{requestId} LIMIT 1")
    AiChargeIntent selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT COUNT(*) FROM ai_charge_intent WHERE user_id = #{userId} "
            + "AND status IN ('PREPARING','RESERVED','IN_FLIGHT','RESPONSE_RECEIVED','MESSAGES_PERSISTED','SETTLEMENT_PENDING','REFUND_PENDING','MANUAL_REVIEW')")
    int countActiveForUser(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(reserved_points), 0) FROM ai_charge_intent WHERE user_id = #{userId} "
            + "AND status IN ('PREPARING','RESERVED','IN_FLIGHT','RESPONSE_RECEIVED','MESSAGES_PERSISTED','SETTLEMENT_PENDING','REFUND_PENDING','MANUAL_REVIEW')")
    BigDecimal sumActiveReservationsForUser(@Param("userId") Long userId);

    @Select("SELECT * FROM ai_charge_intent WHERE "
            + "status IN ('PREPARING','RESERVED','IN_FLIGHT','RESPONSE_RECEIVED','MESSAGES_PERSISTED','SETTLEMENT_PENDING','REFUND_PENDING') "
            + "AND update_time <= #{staleBefore} "
            + "AND (recovery_claimed_at IS NULL OR recovery_claimed_at <= #{staleBefore}) "
            + "ORDER BY update_time ASC LIMIT #{batchSize}")
    List<AiChargeIntent> selectRecoveryCandidates(@Param("staleBefore") LocalDateTime staleBefore,
                                                  @Param("batchSize") int batchSize);

    @Update("UPDATE ai_charge_intent SET recovery_claimed_at = #{now} "
            + "WHERE id = #{id} AND status = #{status} "
            + "AND (recovery_claimed_at IS NULL OR recovery_claimed_at <= #{staleBefore})")
    int claimForRecovery(@Param("id") Long id, @Param("status") String status,
                         @Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'RESERVED', reservation_confirmed = 1, "
            + "update_time = #{now}, recovery_claimed_at = NULL WHERE request_id = #{requestId} AND status = 'PREPARING'")
    int markReserved(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'IN_FLIGHT', upstream_started_at = #{now}, "
            + "update_time = #{now}, recovery_claimed_at = NULL WHERE request_id = #{requestId} AND status = 'RESERVED'")
    int markUpstreamStarted(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'RESPONSE_RECEIVED', actual_points = #{actualPoints}, "
            + "response_received_at = #{now}, update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status IN ('IN_FLIGHT','MANUAL_REVIEW')")
    int markResponseReceived(@Param("requestId") String requestId, @Param("actualPoints") BigDecimal actualPoints,
                             @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'MESSAGES_PERSISTED', update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status = 'RESPONSE_RECEIVED'")
    int markMessagesPersisted(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'SETTLEMENT_PENDING', actual_points = #{actualPoints}, "
            + "update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status = 'MESSAGES_PERSISTED'")
    int markSettlementPending(@Param("requestId") String requestId, @Param("actualPoints") BigDecimal actualPoints,
                              @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'REFUND_PENDING', last_error = #{reason}, "
            + "update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status IN ('PREPARING','RESERVED','IN_FLIGHT','RESPONSE_RECEIVED','REFUND_PENDING')")
    int markRefundPending(@Param("requestId") String requestId, @Param("reason") String reason,
                          @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'REFUNDED', update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status IN ('PREPARING','RESERVED','IN_FLIGHT','RESPONSE_RECEIVED','REFUND_PENDING')")
    int markRefunded(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'SETTLED', update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status = 'SETTLEMENT_PENDING'")
    int markSettled(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = 'MANUAL_REVIEW', last_error = #{reason}, "
            + "update_time = #{now}, recovery_claimed_at = NULL WHERE request_id = #{requestId} AND status = 'IN_FLIGHT'")
    int markManualReview(@Param("requestId") String requestId, @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET status = #{targetStatus}, "
            + "actual_points = COALESCE(#{actualPoints}, actual_points), last_error = #{reason}, "
            + "update_time = #{now}, recovery_claimed_at = NULL "
            + "WHERE request_id = #{requestId} AND status = 'MANUAL_REVIEW'")
    int claimManualReviewResolution(@Param("requestId") String requestId,
                                    @Param("targetStatus") String targetStatus,
                                    @Param("actualPoints") BigDecimal actualPoints,
                                    @Param("reason") String reason,
                                    @Param("now") LocalDateTime now);

    @Update("UPDATE ai_charge_intent SET recovery_claimed_at = NULL, last_error = #{reason} WHERE id = #{id}")
    int releaseRecoveryClaim(@Param("id") Long id, @Param("reason") String reason);
}
