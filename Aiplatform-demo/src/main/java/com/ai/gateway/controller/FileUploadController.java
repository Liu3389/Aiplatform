package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.FileUploadResult;
import com.ai.gateway.dto.ParseStatusDTO;
import com.ai.gateway.service.FileParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传与解析控制器
 * 
 * 提供三大核心接口：
 * 1. POST /v1/file/upload - 批量上传文件（最多8个）
 * 2. GET /v1/file/parse-status/{fileId} - 查询解析状态
 * 3. DELETE /v1/file/{fileId} - 删除未使用的文件
 */
@Slf4j
@RestController
@RequestMapping("/v1/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileParseService fileParseService;

    /**
     * 1. 批量上传文件（最多8个）
     * 
     * 流程：
     * 1. 前端上传多个文件
     * 2. 后端立即返回fileId列表
     * 3. 后台异步解析文件
     * 4. 前端轮询查询解析状态
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        try {
            List<FileUploadResult> results = fileParseService.uploadFiles(files, userId);

            Map<String, Object> data = new HashMap<>();
            data.put("uploadedCount", results.size());
            data.put("files", results);

            return Result.success(data);

        } catch (Exception e) {
            log.error("文件上传失败: userId={}", userId, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 2. 查询文件解析状态
     * 
     * 返回状态：
     * - PENDING: 等待解析
     * - PARSING: 解析中
     * - SUCCESS: 解析成功
     * - FAILED: 解析失败
     */
    @GetMapping("/parse-status/{fileId}")
    public Result<ParseStatusDTO> getParseStatus(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        try {
            ParseStatusDTO status = fileParseService.getParseStatus(fileId, userId);
            return Result.success(status);

        } catch (Exception e) {
            log.error("查询解析状态失败: fileId={}, userId={}", fileId, userId, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 3. 删除未使用的文件
     * 
     * 用户可以取消上传但未在对话中使用的文件
     */
    @DeleteMapping("/{fileId}")
    public Result<Boolean> deleteFile(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }

        try {
            boolean deleted = fileParseService.deleteUnusedFile(fileId, userId);
            return Result.success(deleted);

        } catch (Exception e) {
            log.error("删除文件失败: fileId={}, userId={}", fileId, userId, e);
            return Result.error(e.getMessage());
        }
    }
}
