package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天记录分页请求DTO（游标分页）
 *
 * @author AI Gateway Platform
 */
@Data
@Schema(description = "聊天记录分页请求(游标分页)")
public class ChatMessagePageRequest {

    /**
     * 会话ID
     */
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /**
     * 游标ID（上一页最后一条记录的ID，首次查询不传）
     */
    @Schema(description = "游标ID(上一页最后一条记录的ID,首次查询不传)")
    private Long lastId;

    /**
     * 时间游标。聊天热表和历史表各自自增，不能把两张表的主键当成同一个游标；
     * 新客户端优先使用时间游标，lastId 仅保留给旧客户端兼容。
     */
    @Schema(description = "时间游标(优先于 lastId，首次查询不传)")
    private LocalDateTime lastCreateTime;

    /**
     * 每页条数（默认10）
     */
    @Schema(description = "每页条数(1-50,默认10)", example = "10")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Integer limit = 10;
}
