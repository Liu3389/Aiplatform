package com.ai.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据库初始化器 - 每次启动时重置数据库并生成测试数据
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    
    @Value("${app.init-test-data:false}")
    private boolean initTestData;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!initTestData) {
            log.info("测试数据初始化已禁用（设置 app.init-test-data=true 启用）");
            return;
        }

        log.info("========================================");
        log.info("开始初始化测试数据库...");
        log.info("========================================");

        try {
            // 1. 执行schema.sql创建表结构
            log.info("步骤1: 执行建表脚本...");
            ResourceDatabasePopulator schemaPopulator = new ResourceDatabasePopulator();
            schemaPopulator.setContinueOnError(false);
            schemaPopulator.setSeparator(";");
            schemaPopulator.addScript(new ClassPathResource("sql/schema.sql"));
            schemaPopulator.execute(dataSource);
            log.info("✅ 建表脚本执行成功");

            // 2. 执行init_test_data.sql生成测试数据 (修复 P1-6: 增加文件存在性检查)
            log.info("步骤2: 生成测试数据...");
            Resource initTestResource = new ClassPathResource("sql/init_test_data.sql");
            if (initTestResource.exists()) {
                ResourceDatabasePopulator dataPopulator = new ResourceDatabasePopulator();
                dataPopulator.setContinueOnError(false);
                dataPopulator.setSeparator(";");
                dataPopulator.addScript(initTestResource);
                dataPopulator.execute(dataSource);
                log.info("✅ 测试数据生成成功");
            } else {
                log.warn("⚠️ 未找到 init_test_data.sql，跳过测试数据生成");
            }

            log.info("========================================");
            log.info("✅ 测试数据库初始化完成！");
            log.info("========================================");
            log.info("");
            log.info("测试账户信息:");
            log.info("  - 超级管理员: superadmin01 ~ superadmin05 (密码: Test@123456)");
            log.info("  - 管理员: admin01 ~ admin10 (密码: Test@123456)");
            log.info("  - 测试用户: testuser01 ~ testuser20 (密码: Test@123456)");
            log.info("");
            log.info("========================================");

        } catch (Exception e) {
            log.error("❌ 测试数据库初始化失败", e);
            throw e;
        }
    }
}
