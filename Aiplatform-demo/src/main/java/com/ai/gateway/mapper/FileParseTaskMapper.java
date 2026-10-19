package com.ai.gateway.mapper;

import com.ai.gateway.entity.FileParseTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件解析任务 Mapper
 */
@Mapper
public interface FileParseTaskMapper extends BaseMapper<FileParseTask> {

    /**
     * 根据fileId和userId查询任务
     */
    FileParseTask selectByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") Long userId);

    /** PENDING 首次领取或超时 PARSING 重新领取；随机 token 用于阻止旧 worker 覆盖新结果。 */
    @Update("UPDATE file_parse_task SET parse_status = 'PARSING', parse_start_time = #{now}, " +
            "parse_lease_token = #{leaseToken}, parse_lease_until = #{leaseUntil}, " +
            "parse_attempts = COALESCE(parse_attempts, 0) + 1, error_message = NULL, " +
            "parse_end_time = NULL, last_dispatch_at = NULL " +
            "WHERE id = #{taskId} AND COALESCE(parse_attempts, 0) < #{maxAttempts} AND (" +
            "parse_status = 'PENDING' OR (parse_status = 'PARSING' AND (" +
            "(parse_lease_until IS NOT NULL AND parse_lease_until <= #{now}) OR " +
            "(parse_lease_until IS NULL AND COALESCE(parse_start_time, create_time) <= #{legacyStaleBefore})" +
            ")))" )
    int claimForParsing(@Param("taskId") Long taskId, @Param("leaseToken") String leaseToken,
                        @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil,
                        @Param("legacyStaleBefore") LocalDateTime legacyStaleBefore,
                        @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE file_parse_task SET parse_lease_until = #{leaseUntil} " +
            "WHERE id = #{taskId} AND parse_status = 'PARSING' AND parse_lease_token = #{leaseToken} " +
            "AND parse_lease_until > #{now}")
    int renewParsingLease(@Param("taskId") Long taskId, @Param("leaseToken") String leaseToken,
                         @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Select("SELECT * FROM file_parse_task WHERE (" +
            "(parse_status = 'PENDING' AND (last_dispatch_at IS NULL OR last_dispatch_at <= #{dispatchStaleBefore})) OR " +
            "(parse_status = 'PARSING' AND COALESCE(parse_attempts, 0) < #{maxAttempts} AND " +
            "((parse_lease_until IS NOT NULL AND parse_lease_until <= #{now}) OR " +
            "(parse_lease_until IS NULL AND COALESCE(parse_start_time, create_time) <= #{parseStaleBefore})) AND " +
            "(last_dispatch_at IS NULL OR last_dispatch_at <= #{dispatchStaleBefore}))) " +
            "ORDER BY id ASC LIMIT #{batchSize}")
    List<FileParseTask> selectRecoverableForDispatch(@Param("now") LocalDateTime now,
                                                    @Param("dispatchStaleBefore") LocalDateTime dispatchStaleBefore,
                                                    @Param("parseStaleBefore") LocalDateTime parseStaleBefore,
                                                    @Param("maxAttempts") int maxAttempts,
                                                    @Param("batchSize") int batchSize);

    @Update("UPDATE file_parse_task SET last_dispatch_at = #{now}, " +
            "dispatch_attempts = COALESCE(dispatch_attempts, 0) + 1 " +
            "WHERE id = #{taskId} AND (" +
            "(parse_status = 'PENDING' AND (last_dispatch_at IS NULL OR last_dispatch_at <= #{dispatchStaleBefore})) OR " +
            "(parse_status = 'PARSING' AND COALESCE(parse_attempts, 0) < #{maxAttempts} AND " +
            "((parse_lease_until IS NOT NULL AND parse_lease_until <= #{now}) OR " +
            "(parse_lease_until IS NULL AND COALESCE(parse_start_time, create_time) <= #{parseStaleBefore})) AND " +
            "(last_dispatch_at IS NULL OR last_dispatch_at <= #{dispatchStaleBefore})))")
    int claimForDispatch(@Param("taskId") Long taskId, @Param("dispatchStaleBefore") LocalDateTime dispatchStaleBefore,
                         @Param("parseStaleBefore") LocalDateTime parseStaleBefore,
                         @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE file_parse_task SET last_dispatch_at = NULL WHERE id = #{taskId} AND parse_status IN ('PENDING', 'PARSING')")
    int releaseDispatchClaim(@Param("taskId") Long taskId);

    @Update("UPDATE file_parse_task SET parse_status = 'SUCCESS', parsed_text_length = #{textLength}, " +
            "text_summary = #{summary}, parse_end_time = #{endTime}, parse_lease_token = NULL, " +
            "parse_lease_until = NULL, last_dispatch_at = NULL WHERE id = #{taskId} AND " +
            "parse_status = 'PARSING' AND parse_lease_token = #{leaseToken} AND parse_lease_until > #{endTime}")
    int updateStatusToSuccess(@Param("taskId") Long taskId, @Param("leaseToken") String leaseToken,
                              @Param("textLength") Integer textLength, @Param("summary") String summary,
                              @Param("endTime") LocalDateTime endTime);

    @Update("UPDATE file_parse_task SET parse_status = 'FAILED', error_message = #{errorMessage}, " +
            "parse_end_time = #{endTime}, parse_lease_token = NULL, parse_lease_until = NULL, " +
            "last_dispatch_at = NULL WHERE id = #{taskId} AND parse_status = 'PARSING' " +
            "AND parse_lease_token = #{leaseToken} AND parse_lease_until > #{endTime}")
    int updateStatusToFailed(@Param("taskId") Long taskId, @Param("leaseToken") String leaseToken,
                             @Param("errorMessage") String errorMessage, @Param("endTime") LocalDateTime endTime);

    @Update("UPDATE file_parse_task SET parse_status = 'FAILED', " +
            "error_message = '解析 worker 多次异常退出，任务已停止自动重试', parse_end_time = #{now}, " +
            "parse_lease_token = NULL, parse_lease_until = NULL, last_dispatch_at = NULL " +
            "WHERE parse_status = 'PARSING' AND COALESCE(parse_attempts, 0) >= #{maxAttempts} AND (" +
            "(parse_lease_until IS NOT NULL AND parse_lease_until <= #{now}) OR " +
            "(parse_lease_until IS NULL AND COALESCE(parse_start_time, create_time) <= #{parseStaleBefore}))")
    int failExhaustedExpiredParsing(@Param("now") LocalDateTime now,
                                    @Param("parseStaleBefore") LocalDateTime parseStaleBefore,
                                    @Param("maxAttempts") int maxAttempts);

    /**
     * 标记文件为已使用
     */
    @Update("UPDATE file_parse_task SET is_used = 1, conversation_id = #{conversationId} WHERE file_id = #{fileId}")
    int markAsUsed(@Param("fileId") String fileId, @Param("conversationId") Long conversationId);
}
