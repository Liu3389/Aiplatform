package com.ai.gateway.service;

import com.ai.gateway.entity.AdminOperationLog;
import com.ai.gateway.mapper.AdminOperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理员操作日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOperationLogService {

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 记录管理员操作日志（异步执行，提升响应速度）
     */
    @Async("logExecutor")
    public void logOperation(Long adminId, String adminUsername, String operationType, 
                            String operationAction, List<Long> targetUserIds, 
                            Map<String, Object> detail, String result, String errorMessage) {
        try {
            AdminOperationLog logEntity = new AdminOperationLog();
            logEntity.setAdminId(adminId);
            logEntity.setAdminUsername(adminUsername);
            logEntity.setOperationType(operationType);
            logEntity.setOperationAction(operationAction);
            
            // 将目标用户ID列表转为JSON
            if (targetUserIds != null && !targetUserIds.isEmpty()) {
                logEntity.setTargetUserIds(objectMapper.writeValueAsString(targetUserIds));
            }
            
            // 将操作详情转为JSON
            if (detail != null && !detail.isEmpty()) {
                logEntity.setOperationDetail(objectMapper.writeValueAsString(detail));
            }
            
            logEntity.setResult(result);
            logEntity.setErrorMessage(errorMessage);
            logEntity.setCreateTime(LocalDateTime.now());
            
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                logEntity.setIpAddress(getClientIp(request));
                logEntity.setUserAgent(request.getHeader("User-Agent"));
            }
            
            adminOperationLogMapper.insert(logEntity);
            log.info("管理员操作日志记录成功: adminId={}, type={}, action={}, result={}", 
                    adminId, operationType, operationAction, result);
                    
        } catch (Exception e) {
            log.error("记录管理员操作日志失败", e);
        }
    }

    /**
     * 记录成功操作
     */
    public void logSuccess(Long adminId, String adminUsername, String operationType, 
                          String operationAction, List<Long> targetUserIds, Map<String, Object> detail) {
        logOperation(adminId, adminUsername, operationType, operationAction, targetUserIds, detail, "SUCCESS", null);
    }

    /**
     * 记录失败操作
     */
    public void logFailure(Long adminId, String adminUsername, String operationType, 
                          String operationAction, List<Long> targetUserIds, Map<String, Object> detail, String errorMessage) {
        logOperation(adminId, adminUsername, operationType, operationAction, targetUserIds, detail, "FAILED", errorMessage);
    }

    /**
     * 分页查询操作日志
     */
    public Map<String, Object> getOperationLogs(int page, int size, Long adminId, 
                                                String operationType, String startDate, String endDate) {
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<>();
        
        if (adminId != null) {
            wrapper.eq(AdminOperationLog::getAdminId, adminId);
        }
        
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(AdminOperationLog::getOperationType, operationType);
        }
        
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(AdminOperationLog::getCreateTime, startDate + " 00:00:00");
        }
        
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(AdminOperationLog::getCreateTime, endDate + " 23:59:59");
        }
        
        wrapper.orderByDesc(AdminOperationLog::getCreateTime);
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminOperationLog> logPage = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AdminOperationLog> result = 
            adminOperationLogMapper.selectPage(logPage, wrapper);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("size", size);
        
        return response;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时，第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
