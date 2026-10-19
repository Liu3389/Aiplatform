package com.ai.gateway.init;

import cn.hutool.core.util.IdUtil;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.mapper.ApiKeyMapper;
import com.ai.gateway.service.StatisticsCounterService;
import com.ai.gateway.service.TokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyTestDataInitializer {

    private final ApiKeyMapper apiKeyMapper;
    private final TokenService tokenService;
    private final StatisticsCounterService counterService;

    @Value("${app.init-test-data:false}")
    private boolean initTestData;

    @PostConstruct
    public void init() {
        if (!initTestData) {
            return;
        }

        // 修复 P1-1: 使用缓存计数器替代全表COUNT(*)查询
        Long count = counterService.getCounterValue("APIKEY_TOTAL");
        if (count != null && count > 0) {
            log.info("api_key 表已有 {} 条数据，跳过初始化", count);
            return;
        }

        log.info("开始初始化 api_key 测试数据（AES加密）...");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Object[]> keys = Arrays.asList(
            new Object[]{1L, 1L, "超管默认Key", 1, 9999, "2026-01-15 08:01:00"},
            new Object[]{2L, 2L, "管理员默认Key", 1, 5000, "2026-01-15 08:06:00"},
            new Object[]{3L, 3L, "张三工作Key", 1, 500, "2026-02-01 10:01:00"},
            new Object[]{4L, 3L, "张三家庭Key", 1, 200, "2026-02-10 10:00:00"},
            new Object[]{5L, 4L, "李四Key", 1, 500, "2026-02-02 11:01:00"},
            new Object[]{6L, 5L, "王五Key", 1, 500, "2026-02-03 12:01:00"},
            new Object[]{7L, 6L, "赵六Key", 1, 100, "2026-03-01 09:01:00"},
            new Object[]{8L, 7L, "孙七Key", 0, 100, "2026-03-05 10:01:00"},
            new Object[]{9L, 8L, "周八Key", 1, 100, "2026-03-10 14:01:00"},
            new Object[]{10L, 9L, "吴九Key", 1, 100, "2026-03-15 16:01:00"},
            new Object[]{11L, 10L, "郑十Key", 1, 100, "2026-03-20 08:01:00"},
            new Object[]{12L, 11L, "AliceKey", 1, 50, "2026-04-01 09:01:00"},
            new Object[]{13L, 12L, "BobKey", 1, 50, "2026-04-02 10:01:00"},
            new Object[]{14L, 13L, "CharlieKey", 1, 50, "2026-04-03 11:01:00"},
            new Object[]{15L, 14L, "DavidKey", 1, 50, "2026-04-04 12:01:00"},
            new Object[]{16L, 15L, "EveKey", 1, 50, "2026-04-05 13:01:00"},
            new Object[]{17L, 16L, "low1Key", 1, 30, "2026-04-10 08:01:00"},
            new Object[]{18L, 17L, "low2Key", 1, 30, "2026-04-11 09:01:00"},
            new Object[]{19L, 18L, "low3Key", 1, 30, "2026-04-12 10:01:00"},
            new Object[]{20L, 19L, "low4Key", 1, 30, "2026-04-13 11:01:00"},
            new Object[]{21L, 20L, "new1Key", 1, 50, "2026-05-09 08:01:00"},
            new Object[]{22L, 21L, "new2Key", 1, 50, "2026-05-10 09:01:00"},
            new Object[]{23L, 22L, "new3Key", 1, 50, "2026-05-11 10:01:00"},
            new Object[]{24L, 23L, "new4Key", 1, 50, "2026-05-12 11:01:00"},
            new Object[]{25L, 24L, "new5Key", 1, 50, "2026-05-13 12:01:00"},
            new Object[]{26L, 25L, "new6Key", 1, 50, "2026-05-14 13:01:00"},
            new Object[]{27L, 26L, "new7Key", 1, 50, "2026-05-15 08:01:00"},
            new Object[]{28L, 27L, "new8Key", 1, 50, "2026-05-15 09:01:00"},
            new Object[]{29L, 30L, "cus001Key", 1, 50, "2026-04-15 08:01:00"},
            new Object[]{30L, 31L, "cus002Key", 1, 50, "2026-04-16 09:01:00"},
            new Object[]{31L, 35L, "cus006Key", 1, 100, "2026-04-20 13:01:00"},
            new Object[]{32L, 36L, "cus007Key", 1, 500, "2026-04-21 14:01:00"},
            new Object[]{33L, 38L, "cus009Key", 1, 500, "2026-04-23 16:01:00"},
            new Object[]{34L, 45L, "cus016Key", 1, 500, "2026-04-30 13:01:00"},
            new Object[]{35L, 50L, "cus021Key", 1, 500, "2026-05-05 08:01:00"}
        );

        for (Object[] row : keys) {
            try {
                Long id = (Long) row[0];
                Long userId = (Long) row[1];
                String name = (String) row[2];
                Integer status = (Integer) row[3];
                Integer rateLimit = (Integer) row[4];
                LocalDateTime createTime = LocalDateTime.parse((String) row[5], fmt);

                ApiKey entity = new ApiKey();
                entity.setId(id);
                entity.setApiKey(tokenService.encryptApiKey(createSeedApiKey()));
                entity.setUserId(userId);
                entity.setName(name);
                entity.setStatus(status);
                entity.setRateLimit(rateLimit);
                entity.setCreateTime(createTime);
                apiKeyMapper.insert(entity);
            } catch (Exception e) {
                log.error("插入 api_key 失败: id={}", row[0], e);
            }
        }
        log.info("api_key 测试数据初始化完成，共 {} 条", keys.size());
    }

    static String createSeedApiKey() {
        return "sk-" + IdUtil.simpleUUID();
    }
}
