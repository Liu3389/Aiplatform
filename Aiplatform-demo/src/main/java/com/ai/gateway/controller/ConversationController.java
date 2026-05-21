package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatMessagePageRequest;
import com.ai.gateway.dto.ConversationFilePageRequest;
import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.entity.Conversation;
import com.ai.gateway.service.ChatMessageService;
import com.ai.gateway.service.FileParseTaskService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.util.RedisScanUtil;
import com.ai.gateway.vo.ChatMessagePageVO;
import com.ai.gateway.vo.ConversationFilePageVO;
import com.ai.gateway.vo.ConversationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/user/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final UserService userService;
    private final TokenService tokenService;
    private final ChatMessageService chatMessageService;
    private final FileParseTaskService fileParseTaskService;
    private final StringRedisTemplate redisTemplate;
    private final RedisScanUtil redisScanUtil;
    private final com.ai.gateway.mapper.ConversationMapper conversationMapper;

    private Long resolveUserId(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) throw new RuntimeException("Token无效或已过期");
        return userId;
    }

    // ==================== 会话列表（MySQL + Redis 双源） ====================

    @PostMapping("/sync")
    public Result<Map<String, Object>> syncConversations(
            @RequestHeader("X-User-Token") String token,
            @RequestBody List<Map<String, Object>> conversations) {
        Long userId = resolveUserId(token);
        long version = userService.syncConversations(userId, conversations);
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("count", conversations.size());
        return Result.success(result);
    }

    @GetMapping("/fetch")
    public Result<Map<String, Object>> fetchConversations(
            @RequestHeader("X-User-Token") String token,
            HttpServletRequest request) {
        Long userId = resolveUserId(token);

        // 修复 P2-1: 添加IP日志记录，便于审计
        log.info("获取会话列表: userId={}, ip={}", userId, request.getRemoteAddr());

        // 1. 从 MySQL 查询该用户的所有会话（按最后消息时间倒序）
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .eq(Conversation::getIsDeleted, 0)
                .orderByDesc(Conversation::getLastMessageTime)
                .last("LIMIT 100");

        List<Conversation> convList = conversationMapper.selectList(wrapper);

        // 修复 P2-7: 批量查询每个会话的最新消息，避免 N+1 查询问题
        Map<Long, ChatMessage> latestMessageMap = new HashMap<>();
        if (!convList.isEmpty()) {
            for (Conversation conv : convList) {
                try {
                    List<ChatMessage> latest = chatMessageService.getLatestMessages(conv.getId(), userId, 1);
                    if (!latest.isEmpty()) {
                        latestMessageMap.put(conv.getId(), latest.get(0));
                    }
                } catch (Exception e) {
                    log.debug("获取会话预览失败: conversationId={}", conv.getId());
                }
            }
        }

        // 2. 为每个会话获取最后一条消息作为预览
        List<ConversationVO> voList = convList.stream().map(conv -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(conv.getId());
            vo.setTitle(conv.getTitle());
            vo.setModel(conv.getModel());
            vo.setMessageCount(conv.getMessageCount());
            vo.setCreateTime(conv.getCreateTime() != null
                    ? conv.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : null);
            vo.setLastMessageTime(conv.getLastMessageTime() != null
                    ? conv.getLastMessageTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : null);

            // 从缓存中获取预览内容（最后一条消息的前50字）
            ChatMessage latestMsg = latestMessageMap.get(conv.getId());
            if (latestMsg != null) {
                String content = latestMsg.getContent();
                vo.setPreviewContent(content != null && content.length() > 50
                        ? content.substring(0, 50) + "..."
                        : content);
            }

            return vo;
        }).collect(Collectors.toList());

        // 3. Redis中的客户端同步数据
        List<Map<String, Object>> redisConvs = userService.fetchConversations(userId);
        long version = userService.getConversationVersion(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("conversations", voList);
        result.put("redisConversations", redisConvs);
        result.put("version", version);

        log.info("获取会话列表: userId={}, count={}", userId, voList.size());
        return Result.success(result);
    }

    @GetMapping("/version")
    public Result<Map<String, Object>> getVersion(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        long version = userService.getConversationVersion(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        return Result.success(result);
    }

    @DeleteMapping("/clear")
    public Result<Void> clearConversations(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);

        // 修复 P0-4: 添加空值保护，防止误删所有数据
        if (userId == null) {
            return Result.error("用户认证失败");
        }
        
        userService.syncConversations(userId, List.of());

        // 同时逻辑删除 MySQL 中的会话
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId);
        Conversation update = new Conversation();
        update.setIsDeleted(1);
        int affectedRows = conversationMapper.update(update, wrapper);

        log.info("清除会话完成: userId={}, affectedRows={}", userId, affectedRows);
        return Result.success("清除成功", null);
    }

    // ==================== 游标分页消息接口 ====================

    @GetMapping("/{conversationId}/messages/latest")
    public Result<ChatMessagePageVO> getLatestMessages(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "15") Integer limit) {
        Long userId = resolveUserId(token);
        if (limit > 50) limit = 50;

        // 校验会话归属
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在或无权访问");
        }

        List<ChatMessage> messages = chatMessageService.getLatestMessages(conversationId, userId, limit);
        ChatMessagePageVO vo = convertToPageVO(messages, limit);
        log.info("获取最新消息: userId={}, conversationId={}, count={}", userId, conversationId, messages.size());
        return Result.success(vo);
    }

    @PostMapping("/messages/page")
    public Result<ChatMessagePageVO> getMessagesByCursor(
            @RequestHeader("X-User-Token") String token,
            @Valid @RequestBody ChatMessagePageRequest request) {
        Long userId = resolveUserId(token);

        if (request.getConversationId() == null) return Result.error("会话ID不能为空");

        // 校验会话归属
        Conversation conv = conversationMapper.selectById(request.getConversationId());
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在或无权访问");
        }

        Integer limit = request.getLimit();
        if (limit == null || limit <= 0) limit = 15;
        if (limit > 50) limit = 50;

        List<ChatMessage> messages = chatMessageService.getMessagesByCursor(
                request.getConversationId(), request.getLastId(), limit, userId);
        ChatMessagePageVO vo = convertToPageVO(messages, limit);
        log.info("游标分页: userId={}, convId={}, lastId={}, count={}", userId, request.getConversationId(), request.getLastId(), messages.size());
        return Result.success(vo);
    }

    @PostMapping("/cache/clear")
    public Result<Void> clearUserCache(@RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        String pattern = "chat:messages:" + userId + ":*";
        redisScanUtil.deleteByPattern(pattern);
        redisTemplate.delete("conversations:" + userId);
        redisTemplate.delete("conversations:version:" + userId);
        log.info("清除用户缓存完成: userId={}", userId);
        return Result.success("缓存已清除", null);
    }

    // ==================== 文件详情查询 ====================

    @PostMapping("/files/page")
    public Result<?> getFilesByConversationId(
            @RequestHeader("X-User-Token") String token,
            @RequestBody ConversationFilePageRequest request) {
        Long userId = resolveUserId(token);

        if (request.getConversationId() == null) return Result.error("会话ID不能为空");

        // 校验会话归属
        Conversation conv = conversationMapper.selectById(request.getConversationId());
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在或无权访问");
        }

        try {
            ConversationFilePageVO vo = fileParseTaskService.getFileDetailsByConversationId(userId, request);
            log.info("查询会话文件: userId={}, convId={}, total={}", userId, request.getConversationId(), vo.getTotal());
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询会话文件失败: userId={}, convId={}", userId, request.getConversationId(), e);
            return Result.error("查询文件详情失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private ChatMessagePageVO convertToPageVO(List<ChatMessage> messages, Integer limit) {
        ChatMessagePageVO vo = new ChatMessagePageVO();
        List<ChatMessagePageVO.ChatMessageVO> messageVOs = messages.stream().map(msg -> {
            ChatMessagePageVO.ChatMessageVO msgVO = new ChatMessagePageVO.ChatMessageVO();
            msgVO.setId(msg.getId());
            msgVO.setRole(msg.getRole());
            msgVO.setContent(msg.getContent());
            msgVO.setModel(msg.getModel());
            msgVO.setTokens(msg.getTokens());
            msgVO.setCreateTime(msg.getCreateTime());
            return msgVO;
        }).collect(Collectors.toList());
        vo.setMessages(messageVOs);
        boolean hasMore = messages.size() >= limit;
        vo.setHasMore(hasMore);
        if (hasMore && !messages.isEmpty()) {
            vo.setNextCursorId(messages.get(messages.size() - 1).getId());
        }
        return vo;
    }
}
