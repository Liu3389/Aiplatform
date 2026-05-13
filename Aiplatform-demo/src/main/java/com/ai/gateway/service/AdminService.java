package com.ai.gateway.service;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.ApiKeyMapper;
import com.ai.gateway.mapper.CallLogMapper;
import com.ai.gateway.vo.SystemStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserService userService;
    private final ApiKeyMapper apiKeyMapper;
    private final CallLogMapper callLogMapper;

    public SystemStatsVO getSystemStats() {
        SystemStatsVO stats = new SystemStatsVO();

        stats.setTotalUsers(userService.countUsers());
        stats.setActiveUsers(userService.countActiveUsers());

        Long totalApiKeys = apiKeyMapper.selectCount(null);
        stats.setTotalApiKeys(totalApiKeys);

        List<User> users = userService.listAllUsers();
        List<SystemStatsVO.UserStatVO> userStats = users.stream().map(user -> {
            SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setEmail(user.getEmail());
            vo.setBalance(user.getBalance());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

            LambdaQueryWrapper<ApiKey> keyWrapper = new LambdaQueryWrapper<>();
            keyWrapper.eq(ApiKey::getUserId, user.getId());
            vo.setApiKeyCount(apiKeyMapper.selectCount(keyWrapper).intValue());

            vo.setTotalCalls(0L);
            vo.setTotalCost(BigDecimal.ZERO);
            vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
            return vo;
        }).collect(Collectors.toList());

        stats.setUsers(userStats);
        stats.setTotalCalls(0L);
        stats.setTotalCost(BigDecimal.ZERO);
        stats.setTodayCalls(0L);
        stats.setTodayCost(BigDecimal.ZERO);

        return stats;
    }

    public SystemStatsVO.UserStatVO getUserDetail(Long userId) {
        User user = userService.getUserEntityById(userId);
        SystemStatsVO.UserStatVO vo = new SystemStatsVO.UserStatVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setBalance(user.getBalance());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus() == 1 ? "ACTIVE" : "DISABLED");

        LambdaQueryWrapper<ApiKey> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(ApiKey::getUserId, userId);
        vo.setApiKeyCount(apiKeyMapper.selectCount(keyWrapper).intValue());

        vo.setTotalCalls(0L);
        vo.setTotalCost(BigDecimal.ZERO);
        vo.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);

        return vo;
    }

    public List<Map<String, Object>> getDailyUsage() {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", today.minusDays(i).toString());
            item.put("calls", new Random().nextInt(500) + 100);
            item.put("tokens", new Random().nextInt(50000) + 10000);
            item.put("cost", Math.round((Math.random() * 50 + 5) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getModelUsage() {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] models = {"deepseek-v4-pro", "deepseek-v4-flash", "gpt-4o", "gpt-3.5-turbo"};
        String[] colors = {"#818cf8", "#6366f1", "#14b8a6", "#f59e0b"};
        for (int i = 0; i < models.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("model", models[i]);
            item.put("calls", new Random().nextInt(300) + 50);
            item.put("tokens", new Random().nextInt(30000) + 5000);
            item.put("cost", Math.round((Math.random() * 30 + 2) * 100.0) / 100.0);
            item.put("color", colors[i]);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getHourlyTraffic() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", String.format("%02d:00", h));
            item.put("calls", new Random().nextInt(80) + 5);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getTopUsers() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<User> users = userService.listAllUsers();
        int rank = 1;
        for (User user : users.stream().sorted((a, b) -> b.getBalance().compareTo(a.getBalance())).limit(10).toList()) {
            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("username", user.getUsername());
            item.put("userId", user.getId());
            item.put("calls", new Random().nextInt(100) + 10);
            item.put("cost", Math.round((Math.random() * 20 + 1) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getRevenueTrend() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = 4; m >= 0; m--) {
            Map<String, Object> item = new HashMap<>();
            item.put("month", LocalDate.now().minusMonths(m).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            item.put("revenue", Math.round((Math.random() * 1000 + 200) * 100.0) / 100.0);
            item.put("costs", Math.round((Math.random() * 800 + 100) * 100.0) / 100.0);
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getUserGrowth() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = 4; m >= 0; m--) {
            int total = 10 + (5 - m) * new Random().nextInt(20);
            Map<String, Object> item = new HashMap<>();
            item.put("month", LocalDate.now().minusMonths(m).format(DateTimeFormatter.ofPattern("yyyy-MM")));
            item.put("total", total);
            item.put("active", total * 3 / 4);
            item.put("new", new Random().nextInt(10) + 1);
            result.add(item);
        }
        return result;
    }
}
