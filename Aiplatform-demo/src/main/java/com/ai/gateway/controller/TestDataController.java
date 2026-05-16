package com.ai.gateway.controller;

import com.ai.gateway.annotation.RequireAdmin;
import com.ai.gateway.common.Result;
import com.ai.gateway.service.AdminOperationLogService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 测试数据管理控制器
 * 用于在开发环境中快速重置或修复平台基础配置数据
 */
@Slf4j
@RestController
@RequestMapping("/admin/test-data")
@RequiredArgsConstructor
public class TestDataController {

    private final DataSource dataSource;
    private final AdminOperationLogService operationLogService;
    private final UserService userService;
    private final TokenService tokenService;

    @Value("${app.allow-data-reset:false}")
    private boolean allowDataReset;

    private void checkEnvAllowance() {
        if (!allowDataReset) {
            throw new RuntimeException("数据重置功能未启用，请设置 app.allow-data-reset=true 环境变量");
        }
    }

    /**
     * 重新初始化所有平台基础测试数据
     * 注意：此操作会清空 platform_model_config, marketplace_provider, package_template 表
     */
    @RequireAdmin
    @org.springframework.context.annotation.Profile({"dev", "test"})
    @PostMapping("/reset")
    public Result<String> resetTestData(@RequestHeader("X-User-Token") String token) {
        checkEnvAllowance();
        try {
            log.info("开始执行测试数据重置...");
            
            // 读取 SQL 脚本
            ClassPathResource resource = new ClassPathResource("sql/init_test_data.sql");
            
            // 使用 Spring ScriptUtils 安全执行（修复 P1-39: 避免简单 split 导致的问题）
            ScriptUtils.executeSqlScript(dataSource.getConnection(), resource);
            
            log.info("测试数据重置成功");
            
            // 修复 P0-3: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "SYSTEM_CONFIG", 
                    "RESET_TEST_DATA", null, java.util.Map.of("status", "SUCCESS"));
            
            return Result.success("平台基础测试数据已重置", null);
        } catch (Exception e) {
            log.error("重置测试数据失败", e);
            
            // 修复 P0-4: 记录失败日志
            try {
                Long adminId = tokenService.getUserIdFromToken(token);
                com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
                String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
                operationLogService.logFailure(adminId, adminUsername, "SYSTEM_CONFIG", 
                        "RESET_TEST_DATA", null, java.util.Map.of(), e.getMessage());
            } catch (Exception ex) {
                log.error("记录失败日志异常", ex);
            }
            
            return Result.error("重置失败: " + e.getMessage());
        }
    }

    /**
     * 修复模型点数配置
     * 将所有未配置固定点数的模型统一设置为默认值
     */
    @RequireAdmin
    @org.springframework.context.annotation.Profile("dev")
    @PostMapping("/fix-points")
    public Result<String> fixModelPoints(@RequestHeader("X-User-Token") String token) {
        checkEnvAllowance();
        try {
            org.springframework.jdbc.core.JdbcTemplate jt = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            jt.update("UPDATE platform_model_config SET fixed_points = 3.000 WHERE fixed_points IS NULL OR fixed_points <= 0");
            
            // 修复 P0-3: 记录操作日志
            Long adminId = tokenService.getUserIdFromToken(token);
            com.ai.gateway.entity.User adminUser = userService.getUserEntityById(adminId);
            String adminUsername = adminUser != null ? adminUser.getUsername() : "Unknown_Admin";
            operationLogService.logSuccess(adminId, adminUsername, "SYSTEM_CONFIG", 
                    "FIX_MODEL_POINTS", null, java.util.Map.of("status", "SUCCESS"));
            
            return Result.success("模型点数配置已修复", null);
        } catch (Exception e) {
            return Result.error("修复失败: " + e.getMessage());
        }
    }
}
