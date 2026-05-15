package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日点数发放服务
 * 每天凌晨0点给所有活跃用户发放30点（限时当日有效）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPointsService {

    private final UserMapper userMapper;
    private final PointsService pointsService;

    /**
     * 每日凌晨0点执行，给用户发放30点
     * cron表达式: 秒 分 时 日 月 周
     * 0 0 0 * * ? 表示每天0点执行
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void distributeDailyPoints() {
        log.info("开始执行每日点数发放任务...");
        
        // 查询所有状态为启用的用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1); // 只给启用状态的用户发放
        
        List<User> users = userMapper.selectList(wrapper);
        
        // 修复 P2-7: 采用分批处理，防止万级用户阻塞定时任务
        int batchSize = 500;
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < users.size(); i += batchSize) {
            List<User> batch = users.subList(i, Math.min(i + batchSize, users.size()));
            for (User user : batch) {
                try {
                    LocalDateTime expireTime = LocalDate.now().atTime(23, 59, 59);
                    String businessId = "daily_" + LocalDate.now() + "_" + user.getId();
                    
                    pointsService.addPoints(
                        user.getId(), 
                        new BigDecimal("30.000"), 
                        "GRANT", 
                        businessId, 
                        "每日免费点数（当日有效）", 
                        expireTime
                    );
                    
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("用户 {} 每日点数发放失败", user.getUsername(), e);
                }
            }
        }
        
        log.info("每日点数发放任务完成: 成功={}, 失败={}, 总计={}", 
                successCount, failCount, users.size());
    }
    
    /**
     * 手动触发每日点数发放（用于测试或补发）
     * @param userId 用户ID，如果为null则给所有用户发放
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualDistributePoints(Long userId) {
        if (userId != null) {
            // 给指定用户发放
            User user = userMapper.selectById(userId);
            if (user == null || user.getStatus() != 1) {
                throw new RuntimeException("用户不存在或已禁用");
            }
            
            LocalDateTime expireTime = LocalDate.now().atTime(23, 59, 59);
            String businessId = "manual_" + LocalDate.now() + "_" + userId;
            
            pointsService.addPoints(
                userId, 
                new BigDecimal("30.000"), 
                "GRANT", 
                businessId, 
                "手动发放每日点数（当日有效）", 
                expireTime
            );
            
            log.info("手动给用户 {} 发放30点成功", userId);
        } else {
            // 给所有用户发放
            distributeDailyPoints();
        }
    }
}
