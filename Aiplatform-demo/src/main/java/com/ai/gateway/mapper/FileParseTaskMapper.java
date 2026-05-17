package com.ai.gateway.mapper;

import com.ai.gateway.entity.FileParseTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 文件解析任务 Mapper
 */
@Mapper
public interface FileParseTaskMapper extends BaseMapper<FileParseTask> {

    /**
     * 根据fileId和userId查询任务
     */
    FileParseTask selectByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") Long userId);

    /**
     * 更新解析状态为PARSING
     */
    @Update("UPDATE file_parse_task SET parse_status = 'PARSING', parse_start_time = #{startTime} WHERE id = #{taskId}")
    int updateStatusToParsing(@Param("taskId") Long taskId, @Param("startTime") LocalDateTime startTime);

    /**
     * 更新解析状态为SUCCESS
     */
    @Update("UPDATE file_parse_task SET parse_status = 'SUCCESS', parsed_text_length = #{textLength}, parse_end_time = #{endTime} WHERE id = #{taskId}")
    int updateStatusToSuccess(@Param("taskId") Long taskId, @Param("textLength") Integer textLength, @Param("endTime") LocalDateTime endTime);

    /**
     * 更新解析状态为FAILED
     */
    @Update("UPDATE file_parse_task SET parse_status = 'FAILED', error_message = #{errorMessage}, parse_end_time = #{endTime} WHERE id = #{taskId}")
    int updateStatusToFailed(@Param("taskId") Long taskId, @Param("errorMessage") String errorMessage, @Param("endTime") LocalDateTime endTime);

    /**
     * 标记文件为已使用
     */
    @Update("UPDATE file_parse_task SET is_used = 1, conversation_id = #{conversationId} WHERE file_id = #{fileId}")
    int markAsUsed(@Param("fileId") String fileId, @Param("conversationId") Long conversationId);
}
