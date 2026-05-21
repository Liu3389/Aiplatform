package com.ai.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件解析状态DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseStatusDTO {

    /**
     * 文件唯一ID
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 解析状态：PENDING/PARSING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 解析后文本长度（仅SUCCESS时返回）
     */
    private Integer parsedTextLength;

    /**
     * 错误信息（仅FAILED时返回）
     */
    private String errorMessage;

    /**
     * 上传时间戳
     */
    private Long uploadTime;

    /**
     * 开始解析时间戳（仅PARSING/SUCCESS/FAILED时返回）
     */
    private Long parseStartTime;

    /**
     * 解析完成时间戳（仅SUCCESS/FAILED时返回）
     */
    private Long parseEndTime;
}
