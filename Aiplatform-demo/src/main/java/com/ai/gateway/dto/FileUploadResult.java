package com.ai.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult {
    
    /**
     * 文件唯一ID
     */
    private String fileId;
    
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 解析状态
     */
    private String status;
    
    /**
     * 上传时间戳
     */
    private Long uploadTime;
}
