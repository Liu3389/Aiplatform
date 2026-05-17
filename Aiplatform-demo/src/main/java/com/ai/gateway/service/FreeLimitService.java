package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FreeLimitService {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private DefaultRedisScript<Long> freeLimitScript;

    private static final String MEMBERSHIP_CACHE_PREFIX = "membership:";
    private static final long MEMBERSHIP_TTL_HOURS = 1;

    @PostConstruct
    public void init() {
        freeLimitScript = new DefaultRedisScript<>();
        freeLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/free_limit_check.lua")));
        freeLimitScript.setResultType(Long.class);
    }

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
        // 修复 P1-3: 使用 Lua 脚本保证“检查+递增”的原子性
        String membership = getMembership(userId);
        int maxCalls = FREE_DAILY_CALLS;
        if ("plus".equals(membership)) maxCalls = PLUS_DAILY_CALLS;
        else if ("pro".equals(membership)) maxCalls = PRO_DAILY_CALLS;

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String callKey = FREE_CALLS_PREFIX + userId + ":" + today;

        try {
            Long result = redisTemplate.execute(freeLimitScript, List.of(callKey), String.valueOf(maxCalls), "90000");
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("免费配额检查失败", e);
            return false;
        }
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
        String cacheKey = MEMBERSHIP_CACHE_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        try {
            User user = userService.getUserEntityById(userId);
            String membership = user != null && user.getMembership() != null ? user.getMembership() : "free";
            long ttlWithJitter = MEMBERSHIP_TTL_HOURS * 3600 + ThreadLocalRandom.current().nextInt(300);
            redisTemplate.opsForValue().set(cacheKey, membership, ttlWithJitter, TimeUnit.SECONDS);
            return membership;
        } catch (Exception e) {
            return "free";
        }
    }

    public void clearMembershipCache(Long userId) {
        redisTemplate.delete(MEMBERSHIP_CACHE_PREFIX + userId);
    }
}
