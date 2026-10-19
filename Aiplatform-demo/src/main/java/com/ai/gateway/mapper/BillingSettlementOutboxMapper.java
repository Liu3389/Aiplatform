package com.ai.gateway.mapper;

import com.ai.gateway.entity.BillingSettlementOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BillingSettlementOutboxMapper extends BaseMapper<BillingSettlementOutbox> {

    @Select("SELECT * FROM billing_settlement_outbox WHERE status = 'PENDING' " +
            "AND (last_attempt_at IS NULL OR last_attempt_at <= #{staleBefore}) " +
            "ORDER BY id ASC LIMIT #{batchSize}")
    List<BillingSettlementOutbox> selectPublishCandidates(@Param("staleBefore") LocalDateTime staleBefore,
                                                          @Param("batchSize") int batchSize);

    /** 多实例下只有一个投递器能取得本轮发布权；过期后允许另一个实例接管。 */
    @Update("UPDATE billing_settlement_outbox SET last_attempt_at = #{now}, " +
            "dispatch_attempts = dispatch_attempts + 1 WHERE id = #{id} AND status = 'PENDING' " +
            "AND (last_attempt_at IS NULL OR last_attempt_at <= #{staleBefore})")
    int claimForPublish(@Param("id") Long id, @Param("staleBefore") LocalDateTime staleBefore,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE billing_settlement_outbox SET last_attempt_at = NULL, last_error = #{error} " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markPublishFailure(@Param("id") Long id, @Param("error") String error);

    @Update("UPDATE billing_settlement_outbox SET status = 'SETTLED', settled_at = #{now}, last_error = NULL " +
            "WHERE id = #{id} AND status <> 'SETTLED'")
    int markSettled(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE billing_settlement_outbox SET process_attempts = process_attempts + 1, last_error = #{error} " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markProcessingFailure(@Param("id") Long id, @Param("error") String error);
}
