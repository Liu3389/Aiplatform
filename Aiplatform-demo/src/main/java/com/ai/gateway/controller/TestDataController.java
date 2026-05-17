package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.entity.Conversation;
import com.ai.gateway.mapper.ConversationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 测试数据控制器 - 用于初始化和修复测试数据
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@RestController
@RequestMapping("/admin/test-data")
@RequiredArgsConstructor
public class TestDataController {

    private final ConversationMapper conversationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CONVERSATIONS_KEY = "conversations:";
    private static final String CONVERSATIONS_VERSION_KEY = "conversations:version:";

    /**
     * 初始化用户对话缓存（从数据库同步到Redis）
     * 
     * @param userId 用户ID
     * @return 操作结果
     */
    @PostMapping("/init-conversation-cache")
    public Result<Map<String, Object>> initConversationCache(@RequestParam Long userId) {
        try {
            // 1. 从数据库查询用户的所有会话
            List<Conversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getUserId, userId)
                    .eq(Conversation::getIsDeleted, 0)
                    .orderByDesc(Conversation::getLastMessageTime)
            );

            // 2. 转换为前端需要的格式
            List<Map<String, Object>> convList = conversations.stream()
                .map(conv -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", String.valueOf(conv.getId()));
                    map.put("title", conv.getTitle());
                    map.put("model", conv.getModel());
                    map.put("messageCount", conv.getMessageCount());
                    map.put("lastMessageTime", conv.getLastMessageTime());
                    map.put("createTime", conv.getCreateTime());
                    return map;
                })
                .collect(Collectors.toList());

            // 3. 写入Redis
            String json = objectMapper.writeValueAsString(convList);
            redisTemplate.opsForValue().set(
                CONVERSATIONS_KEY + userId, 
                json, 
                90, 
                TimeUnit.DAYS
            );

            // 4. 设置版本号
            long version = System.currentTimeMillis();
            redisTemplate.opsForValue().set(
                CONVERSATIONS_VERSION_KEY + userId,
                String.valueOf(version),
                90,
                TimeUnit.DAYS
            );

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("conversationCount", convList.size());
            result.put("version", version);

            log.info("✅ 用户{}的对话缓存初始化完成，共{}个会话", userId, convList.size());
            
            return Result.success("缓存初始化成功", result);

        } catch (Exception e) {
            log.error("❌ 初始化对话缓存失败: userId={}", userId, e);
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    /**
     * 批量初始化所有用户的对话缓存
     * 
     * @return 操作结果
     */
    @PostMapping("/init-all-conversation-cache")
    public Result<Map<String, Object>> initAllConversationCache() {
        try {
            // 查询所有有会话的用户
            List<Long> userIds = conversationMapper.selectObjs(
                new LambdaQueryWrapper<Conversation>()
                    .select(Conversation::getUserId)
                    .eq(Conversation::getIsDeleted, 0)
                    .groupBy(Conversation::getUserId)
            ).stream().map(obj -> (Long) obj).collect(Collectors.toList());

            int successCount = 0;
            int failCount = 0;
            Map<Long, Integer> userConvCount = new HashMap<>();

            for (Long userId : userIds) {
                try {
                    List<Conversation> conversations = conversationMapper.selectList(
                        new LambdaQueryWrapper<Conversation>()
                            .eq(Conversation::getUserId, userId)
                            .eq(Conversation::getIsDeleted, 0)
                            .orderByDesc(Conversation::getLastMessageTime)
                    );

                    List<Map<String, Object>> convList = conversations.stream()
                        .map(conv -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", String.valueOf(conv.getId()));
                            map.put("title", conv.getTitle());
                            map.put("model", conv.getModel());
                            map.put("messageCount", conv.getMessageCount());
                            map.put("lastMessageTime", conv.getLastMessageTime());
                            map.put("createTime", conv.getCreateTime());
                            return map;
                        })
                        .collect(Collectors.toList());

                    String json = objectMapper.writeValueAsString(convList);
                    redisTemplate.opsForValue().set(
                        CONVERSATIONS_KEY + userId, 
                        json, 
                        90, 
                        TimeUnit.DAYS
                    );

                    long version = System.currentTimeMillis();
                    redisTemplate.opsForValue().set(
                        CONVERSATIONS_VERSION_KEY + userId,
                        String.valueOf(version),
                        90,
                        TimeUnit.DAYS
                    );

                    userConvCount.put(userId, convList.size());
                    successCount++;
                    
                    log.info("✅ 用户{}的对话缓存初始化完成，共{}个会话", userId, convList.size());

                } catch (Exception e) {
                    log.error("❌ 用户{}的对话缓存初始化失败", userId, e);
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalUsers", userIds.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("userConversationCount", userConvCount);

            log.info("✅ 批量初始化完成: 总用户数={}, 成功={}, 失败={}", userIds.size(), successCount, failCount);
            
            return Result.success("批量初始化完成", result);

        } catch (Exception e) {
            log.error("❌ 批量初始化对话缓存失败", e);
            return Result.error("批量初始化失败: " + e.getMessage());
        }
    }

    /**
     * 查看用户的对话缓存状态
     * 
     * @param userId 用户ID
     * @return 缓存状态
     */
    @GetMapping("/check-conversation-cache")
    public Result<Map<String, Object>> checkConversationCache(@RequestParam Long userId) {
        try {
            String conversationsJson = redisTemplate.opsForValue().get(CONVERSATIONS_KEY + userId);
            String versionStr = redisTemplate.opsForValue().get(CONVERSATIONS_VERSION_KEY + userId);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            
            if (conversationsJson != null) {
                List<Map<String, Object>> conversations = objectMapper.readValue(
                    conversationsJson, 
                    List.class
                );
                result.put("hasCache", true);
                result.put("conversationCount", conversations.size());
                result.put("version", versionStr);
            } else {
                result.put("hasCache", false);
                result.put("conversationCount", 0);
                result.put("version", null);
            }

            // 查询数据库中的实际数量
            long dbCount = conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getUserId, userId)
                    .eq(Conversation::getIsDeleted, 0)
            );
            result.put("dbConversationCount", dbCount);

            return Result.success(result);

        } catch (Exception e) {
            log.error("❌ 检查对话缓存失败: userId={}", userId, e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 清除用户的对话缓存
     * 
     * @param userId 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/clear-conversation-cache")
    public Result<Void> clearConversationCache(@RequestParam Long userId) {
        try {
            redisTemplate.delete(CONVERSATIONS_KEY + userId);
            redisTemplate.delete(CONVERSATIONS_VERSION_KEY + userId);
            
            log.info("✅ 用户{}的对话缓存已清除", userId);
            
            return Result.success("缓存已清除", null);

        } catch (Exception e) {
            log.error("❌ 清除对话缓存失败: userId={}", userId, e);
            return Result.error("清除失败: " + e.getMessage());
        }
    }
}
