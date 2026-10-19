package com.ai.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传结果")
public class FileUploadResult {

    /**
     * 文件唯一ID
     */
    @Schema(description = "文件唯一ID")
    private String fileId;

    /**
     * 原始文件名
     */
    @Schema(description = "原始文件名")
    private String fileName;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    /**
     * 文件类型
     */
    @Schema(description = "文件类型")
    private String fileType;

    /**
     * 解析状态
     */
    @Schema(description = "解析状态")
    private String status;

    /**
     * 上传时间戳
     */
    @Schema(description = "上传时间戳")
    private Long uploadTime;

    /**
     * 文件访问 URL（OSS 为公网 HTTPS 地址，本地降级为 /api/uploads/ 相对路径）
     */
    @Schema(description = "文件可访问URL（OSS公网地址或本地/api/uploads/路径）")
    private String ossUrl;
}
