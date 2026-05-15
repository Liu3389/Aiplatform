package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 测试数据管理控制器
 * 用于在开发环境中快速重置或修复平台基础配置数据
 */
@Slf4j
@RestController
@RequestMapping("/admin/test-data")
@RequiredArgsConstructor
public class TestDataController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 重新初始化所有平台基础测试数据
     * 注意：此操作会清空 platform_model_config, marketplace_provider, package_template 表
     */
    @PostMapping("/reset")
    public Result<String> resetTestData() {
        try {
            log.info("开始执行测试数据重置...");
            
            // 读取 SQL 脚本
            ClassPathResource resource = new ClassPathResource("sql/init_test_data.sql");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            String sqlScript = reader.lines().collect(Collectors.joining("\n"));
            
            // 简单分割并执行（生产环境建议使用 Flyway/Liquibase）
            String[] statements = sqlScript.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    try {
                        jdbcTemplate.execute(trimmed);
                    } catch (Exception e) {
                        log.warn("执行SQL片段跳过: {}", e.getMessage());
                    }
                }
            }
            
            log.info("测试数据重置成功");
            return Result.success("平台基础测试数据已重置", null);
        } catch (Exception e) {
            log.error("重置测试数据失败", e);
            return Result.error("重置失败: " + e.getMessage());
        }
    }

    /**
     * 修复模型点数配置
     * 将所有未配置固定点数的模型统一设置为默认值
     */
    @PostMapping("/fix-points")
    public Result<String> fixModelPoints() {
        try {
            jdbcTemplate.update("UPDATE platform_model_config SET fixed_points = 3.000 WHERE fixed_points IS NULL OR fixed_points <= 0");
            return Result.success("模型点数配置已修复", null);
        } catch (Exception e) {
            return Result.error("修复失败: " + e.getMessage());
        }
    }
}
