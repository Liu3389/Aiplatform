package com.ai.gateway.mapper;

import com.ai.gateway.entity.AiChargeReviewAudit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiChargeReviewAuditMapper extends BaseMapper<AiChargeReviewAudit> {

    @Select("SELECT * FROM ai_charge_review_audit WHERE request_id = #{requestId} LIMIT 1")
    AiChargeReviewAudit selectByRequestId(@Param("requestId") String requestId);

    @Update("UPDATE ai_charge_review_audit SET status = #{status}, last_error = #{lastError}, "
            + "update_time = #{now}, completed_at = #{completedAt} WHERE request_id = #{requestId}")
    int updateOutcome(@Param("requestId") String requestId, @Param("status") String status,
                      @Param("lastError") String lastError, @Param("now") LocalDateTime now,
                      @Param("completedAt") LocalDateTime completedAt);
}
