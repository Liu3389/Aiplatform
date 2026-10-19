package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatMessagePageRequest;
import com.ai.gateway.dto.ConversationFilePageRequest;
import com.ai.gateway.dto.DeleteMessageRequest;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "02-用户中心-会话", description = "会话列表同步/拉取、消息分页、会话文件查询")
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
    private final com.ai.gateway.mapper.ChatMessageMapper chatMessageMapper;

    private Long resolveUserId(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) throw new RuntimeException("Token无效或已过期");
        return userId;
    }

    // ==================== 会话列表（MySQL + Redis 双源） ====================

    @Operation(summary = "同步客户端会话列表", description = "上传客户端会话列表，返回版本号")
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

    @Operation(summary = "拉取会话列表", description = "MySQL + Redis 双源合并，含最新消息预览，最多100条")
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

    @Operation(summary = "获取会话列表版本号")
    @GetMapping("/version")
    public Result<Map<String, Object>> getVersion(
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        long version = userService.getConversationVersion(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        return Result.success(result);
    }

    @Operation(summary = "清除当前用户所有会话", description = "逻辑删除 MySQL 会话并清空 Redis 同步数据")
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

    @Operation(summary = "获取会话最新消息", description = "limit 上限 50")
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

    @Operation(summary = "游标分页查询消息", description = "按时间游标向前翻页，兼容旧版 lastId，limit 上限 50")
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
                request.getConversationId(), request.getLastId(), request.getLastCreateTime(), limit, userId);
        ChatMessagePageVO vo = convertToPageVO(messages, limit);
        log.info("游标分页: userId={}, convId={}, lastId={}, lastCreateTime={}, count={}",
                userId, request.getConversationId(), request.getLastId(), request.getLastCreateTime(), messages.size());
        return Result.success(vo);
    }

    @Operation(summary = "清除当前用户消息缓存", description = "删除 Redis 中该用户的消息与会话缓存")
    @PostMapping("/cache/clear")
    public Result<Void> clearUserCache(@RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        // 消息缓存键格式为 chat:messages:v2:{userId}:{conversationId}:...，
        // 旧实现把 userId 当成 conversationId，导致清缓存接口实际没有删除任何消息缓存。
        String pattern = "chat:messages:v2:" + userId + ":*";
        redisScanUtil.deleteByPattern(pattern);
        redisTemplate.delete("conversations:" + userId);
        redisTemplate.delete("conversations:version:" + userId);
        log.info("清除用户缓存完成: userId={}", userId);
        return Result.success("缓存已清除", null);
    }

    @Operation(summary = "删除单条消息", description = "按 conversationId + role + content 精确定位删除最近一条，确保跨设备同步")
    @DeleteMapping("/{conversationId}/messages")
    public Result<Void> deleteMessage(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long conversationId,
            @RequestBody DeleteMessageRequest request) {
        Long userId = resolveUserId(token);

        if (request == null || request.getRole() == null || request.getContent() == null) {
            return Result.error("role 和 content 不能为空");
        }

        // 校验会话归属
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在或无权访问");
        }

        boolean ok = chatMessageService.deleteMessage(conversationId, userId, request.getRole(), request.getContent());
        log.info("删除消息: userId={}, convId={}, role={}, contentLen={}, ok={}",
                userId, conversationId, request.getRole(),
                request.getContent() != null ? request.getContent().length() : 0, ok);
        return ok ? Result.success("删除成功", null) : Result.error("消息未找到或已删除");
    }

    @Operation(summary = "删除整个会话", description = "删除会话及其所有消息，同步清除 Redis 缓存，确保跨设备同步")
    @DeleteMapping("/{conversationId}")
    public Result<Void> deleteConversation(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long conversationId) {
        Long userId = resolveUserId(token);

        // 校验会话归属
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在或无权访问");
        }

        // 1. 逻辑删除该会话的所有消息（物理删除会导致 syncConversations 重建时消息丢失）
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
        // 2. 逻辑删除会话本身（设置 is_deleted=1，防止 syncConversations 重建已删除的会话）
        Conversation updateEntity = new Conversation();
        updateEntity.setId(conversationId);
        updateEntity.setIsDeleted(1);
        conversationMapper.updateById(updateEntity);
        // 3. 清除 Redis 缓存（会话列表 + 版本号 + 该会话的消息缓存）
        redisTemplate.delete("conversations:" + userId);
        redisTemplate.delete("conversations:version:" + userId);
        redisScanUtil.deleteByPattern("chat:messages:v2:" + userId + ":" + conversationId + ":*");

        log.info("删除会话: userId={}, conversationId={}", userId, conversationId);
        return Result.success("删除成功", null);
    }

    // ==================== 文件详情查询 ====================

    @Operation(summary = "分页查询会话关联文件")
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
        int safeLimit = Math.min(Math.max(limit == null ? 15 : limit, 1), 50);
        boolean hasMore = messages.size() > safeLimit;
        // service 为判断 hasMore 多取一条，并在这里返回“最新的 safeLimit 条”。
        // 不能取 subList(0, safeLimit)：service 已经把结果恢复为时间正序，
        // 那样会丢掉最新一条，导致首屏缺消息、下一页游标重复。
        List<ChatMessage> pageMessages = hasMore
                ? messages.subList(messages.size() - safeLimit, messages.size())
                : messages;
        List<ChatMessagePageVO.ChatMessageVO> messageVOs = pageMessages.stream().map(msg -> {
            ChatMessagePageVO.ChatMessageVO msgVO = new ChatMessagePageVO.ChatMessageVO();
            msgVO.setId(msg.getId());
            msgVO.setRole(msg.getRole());
            msgVO.setContent(msg.getContent());
            msgVO.setModel(msg.getModel());
            msgVO.setTokens(msg.getTokens());
            msgVO.setCreateTime(msg.getCreateTime());
            msgVO.setThinkingTime(msg.getThinkingTime());
            // 透传文件元数据：前端据此恢复用户消息上方的文件卡片（含下载链接、过期判断）
            msgVO.setFileUrls(msg.getFileUrls());
            msgVO.setMetadata(msg.getMetadata());
            return msgVO;
        }).collect(Collectors.toList());
        vo.setMessages(messageVOs);
        vo.setHasMore(hasMore);
        if (!pageMessages.isEmpty()) {
            ChatMessage oldest = pageMessages.get(pageMessages.size() - 1);
            vo.setNextCursorId(oldest.getId());
            vo.setNextCursorTime(oldest.getCreateTime());
        }
        return vo;
    }
}
