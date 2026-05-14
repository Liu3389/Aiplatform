package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FreeLimitService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;

    public FreeLimitService(StringRedisTemplate redisTemplate, @Lazy UserService userService) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
    }

    private static final String FREE_CALLS_PREFIX = "free:calls:";
    private static final String FREE_TOKENS_PREFIX = "free:tokens:";

    private static final int FREE_DAILY_CALLS = 30;
    private static final int FREE_DAILY_TOKENS = 50000;
    private static final int PLUS_DAILY_CALLS = 500;
    private static final int PLUS_DAILY_TOKENS = 500000;
    private static final int PRO_DAILY_CALLS = 999999;
    private static final int PRO_DAILY_TOKENS = 2000000;

    public boolean checkAndRecord(Long userId, String model) {
        String membership = getMembership(userId);

        int maxCalls = FREE_DAILY_CALLS;
        int maxTokens = FREE_DAILY_TOKENS;

        switch (membership) {
            case "plus":
                maxCalls = PLUS_DAILY_CALLS;
                maxTokens = PLUS_DAILY_TOKENS;
                break;
            case "pro":
                maxCalls = PRO_DAILY_CALLS;
                maxTokens = PRO_DAILY_TOKENS;
                break;
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String callKey = FREE_CALLS_PREFIX + userId + ":" + today;
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;

        String callCount = redisTemplate.opsForValue().get(callKey);
        int calls = callCount != null ? Integer.parseInt(callCount) : 0;

        if (calls >= maxCalls) return false;

        String tokenUsed = redisTemplate.opsForValue().get(tokenKey);
        int tokens = tokenUsed != null ? Integer.parseInt(tokenUsed) : 0;
        if (tokens >= maxTokens) return false;

        // 同时增加调用次数和预估token数（基础估算）
        redisTemplate.opsForValue().increment(callKey);
        redisTemplate.expire(callKey, 25, TimeUnit.HOURS);
        
        // 为每次调用预设一个基础token数（实际token数在响应后更新）
        redisTemplate.opsForValue().increment(tokenKey, 100); // 预设100 tokens作为基础消耗
        redisTemplate.expire(tokenKey, 25, TimeUnit.HOURS);

        return true;
    }

    public void addTokens(Long userId, int tokens) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;
        redisTemplate.opsForValue().increment(tokenKey, tokens);
        redisTemplate.expire(tokenKey, 25, TimeUnit.HOURS);
    }

    public Map<String, Integer> getUserFreeQuota(Long userId) {
        String membership = getMembership(userId);
        int maxCalls = FREE_DAILY_CALLS;
        int maxTokens = FREE_DAILY_TOKENS;

        switch (membership) {
            case "plus":
                maxCalls = PLUS_DAILY_CALLS;
                maxTokens = PLUS_DAILY_TOKENS;
                break;
            case "pro":
                maxCalls = PRO_DAILY_CALLS;
                maxTokens = PRO_DAILY_TOKENS;
                break;
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String callKey = FREE_CALLS_PREFIX + userId + ":" + today;
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;

        String callCount = redisTemplate.opsForValue().get(callKey);
        String tokenUsed = redisTemplate.opsForValue().get(tokenKey);

        Map<String, Integer> quota = new HashMap<>();
        quota.put("usedCalls", callCount != null ? Integer.parseInt(callCount) : 0);
        quota.put("maxCalls", maxCalls);
        quota.put("usedTokens", tokenUsed != null ? Integer.parseInt(tokenUsed) : 0);
        quota.put("maxTokens", maxTokens);

        int monthlyPoints = 0;
        switch (membership) {
            case "plus": monthlyPoints = 300; break;
            case "pro": monthlyPoints = 1000; break;
        }
        quota.put("monthlyPoints", monthlyPoints);
        return quota;
    }

    private String getMembership(Long userId) {
        try {
            User user = userService.getUserEntityById(userId);
            return user != null && user.getMembership() != null ? user.getMembership() : "free";
        } catch (Exception e) {
            return "free";
        }
    }
}
