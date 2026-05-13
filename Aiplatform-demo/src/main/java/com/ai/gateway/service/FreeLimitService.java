package com.ai.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreeLimitService {

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private static final String FREE_CALLS_PREFIX = "free:calls:";
    private static final String FREE_TOKENS_PREFIX = "free:tokens:";

    private static final int DAILY_FREE_CALLS = 30;
    private static final int DAILY_FREE_TOKENS = 50000;
    private static final int DAILY_UPLOAD_LIMIT = 5 * 1024 * 1024;

    public boolean checkAndRecord(Long userId, String model) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String callKey = FREE_CALLS_PREFIX + userId + ":" + today;
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;

        String callCount = redisTemplate.opsForValue().get(callKey);
        int calls = callCount != null ? Integer.parseInt(callCount) : 0;

        if (calls >= DAILY_FREE_CALLS) return false;

        String tokenUsed = redisTemplate.opsForValue().get(tokenKey);
        int tokens = tokenUsed != null ? Integer.parseInt(tokenUsed) : 0;
        if (tokens >= DAILY_FREE_TOKENS) return false;

        redisTemplate.opsForValue().increment(callKey);
        redisTemplate.expire(callKey, 25, TimeUnit.HOURS);

        return true;
    }

    public void addTokens(Long userId, int tokens) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;
        redisTemplate.opsForValue().increment(tokenKey, tokens);
        redisTemplate.expire(tokenKey, 25, TimeUnit.HOURS);
    }

    public Map<String, Integer> getUserFreeQuota(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String callKey = FREE_CALLS_PREFIX + userId + ":" + today;
        String tokenKey = FREE_TOKENS_PREFIX + userId + ":" + today;

        String callCount = redisTemplate.opsForValue().get(callKey);
        String tokenUsed = redisTemplate.opsForValue().get(tokenKey);

        Map<String, Integer> quota = new java.util.HashMap<>();
        quota.put("usedCalls", callCount != null ? Integer.parseInt(callCount) : 0);
        quota.put("maxCalls", DAILY_FREE_CALLS);
        quota.put("usedTokens", tokenUsed != null ? Integer.parseInt(tokenUsed) : 0);
        quota.put("maxTokens", DAILY_FREE_TOKENS);
        return quota;
    }
}
