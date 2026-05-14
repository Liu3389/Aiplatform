package com.ai.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI模型调度网关与流式对话平台 - 主启动类
 * 
 * @author AI Gateway Team
 * @version 1.0.0
 */
@SpringBootApplication
@MapperScan("com.ai.gateway.mapper")
@EnableScheduling // 启用定时任务
public class AiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
        System.out.println("=========================================");
        System.out.println("AI Gateway Platform 启动成功！");
        System.out.println("访问地址: http://localhost:8080/api");
        System.out.println("=========================================");
    }
}
