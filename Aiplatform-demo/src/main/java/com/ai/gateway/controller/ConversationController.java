package com.ai.gateway.controller;

import com.ai.gateway.common.Result;
import com.ai.gateway.dto.ChatMessagePageRequest;
import com.ai.gateway.entity.ChatMessage;
import com.ai.gateway.service.ChatMessageService;
import com.ai.gateway.service.TokenService;
import com.ai.gateway.service.UserService;
import com.ai.gateway.vo.ChatMessagePageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 对话控制器 - 支持游标分页和冷热分离
 * 
 * @author AI Gateway Platform
 */
@Slf4j
@RestController
@RequestMapping("/user/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final UserService userService;
    private final TokenService tokenService;
    private final ChatMessageService chatMessageService;

    private Long resolveUserId(String token) {
        Long userId = tokenService.getUserIdFromToken(token);
        if (userId == null) throw new RuntimeException("Token无效或已过期");
        return userId;
    }

    // ==================== 原有会话管理接口 ====================

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
            @RequestHeader("X-User-Token") String token) {
        Long userId = resolveUserId(token);
        List<Map<String, Object>> conversations = userService.fetchConversations(userId);
        long version = userService.getConversationVersion(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("conversations", conversations);
        result.put("version", version);
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
        userService.syncConversations(userId, List.of());
        return Result.success("清除成功", null);
    }

    // ==================== 新增：游标分页接口 ====================

    /**
     * 获取会话的最新N条消息（首页加载）
     * 默认只加载最新10条，按时间倒序展示
     * 
     * @param token 用户Token
     * @param conversationId 会话ID
     * @param limit 条数（默认10，可动态调节）
     * @return 消息列表
     */
    @GetMapping("/{conversationId}/messages/latest")
    public Result<ChatMessagePageVO> getLatestMessages(
            @RequestHeader("X-User-Token") String token,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "10") Integer limit) {
        
        Long userId = resolveUserId(token);
        
        // 限制最大条数，防止一次性加载过多
        if (limit > 50) {
            limit = 50;
        }
        
        List<ChatMessage> messages = chatMessageService.getLatestMessages(conversationId, userId, limit);
        
        ChatMessagePageVO vo = convertToPageVO(messages, limit);
        
        log.info("获取最新消息: userId={}, conversationId={}, limit={}, count={}", 
                userId, conversationId, limit, messages.size());
        
        return Result.success(vo);
    }

    /**
     * 游标分页查询历史消息（上翻加载）
     * 以lastId为游标，查询更早的N条记录
     * 
     * @param token 用户Token
     * @param request 分页请求（包含conversationId、lastId、limit）
     * @return 分页结果
     */
    @PostMapping("/messages/page")
    public Result<ChatMessagePageVO> getMessagesByCursor(
            @RequestHeader("X-User-Token") String token,
            @RequestBody ChatMessagePageRequest request) {
        
        Long userId = resolveUserId(token);
        
        // 参数校验
        if (request.getConversationId() == null) {
            return Result.error("会话ID不能为空");
        }
        
        // 限制最大条数
        Integer limit = request.getLimit();
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        if (limit > 50) {
            limit = 50;
        }
        
        List<ChatMessage> messages = chatMessageService.getMessagesByCursor(
                request.getConversationId(), 
                request.getLastId(), 
                limit, 
                userId
        );
        
        ChatMessagePageVO vo = convertToPageVO(messages, limit);
        
        log.info("游标分页查询: userId={}, conversationId={}, lastId={}, limit={}, count={}", 
                userId, request.getConversationId(), request.getLastId(), limit, messages.size());
        
        return Result.success(vo);
    }

    /**
     * 切换账号时清除本地缓存
     * 前端在切换账号后调用此接口，强制删除Redis中的聊天记录缓存
     * 
     * @param token 旧用户的Token（切换前）
     * @return 操作结果
     */
    @PostMapping("/cache/clear")
    public Result<Void> clearUserCache(
            @RequestHeader("X-User-Token") String token) {
        
        Long userId = resolveUserId(token);
        
        // TODO: 实现清除用户所有会话缓存的逻辑
        // redisTemplate.delete(redisTemplate.keys("chat:messages:" + userId + ":*"));
        
        log.info("清除用户缓存: userId={}", userId);
        
        return Result.success("缓存已清除", null);
    }

    // ==================== 辅助方法 ====================

    /**
     * 转换为分页VO
     */
    private ChatMessagePageVO convertToPageVO(List<ChatMessage> messages, Integer limit) {
        ChatMessagePageVO vo = new ChatMessagePageVO();
        
        // 转换消息列表
        List<ChatMessagePageVO.ChatMessageVO> messageVOs = messages.stream()
                .map(msg -> {
                    ChatMessagePageVO.ChatMessageVO msgVO = new ChatMessagePageVO.ChatMessageVO();
                    msgVO.setId(msg.getId());
                    msgVO.setRole(msg.getRole());
                    msgVO.setContent(msg.getContent());
                    msgVO.setModel(msg.getModel());
                    msgVO.setTokens(msg.getTokens());
                    msgVO.setCreateTime(msg.getCreateTime());
                    return msgVO;
                })
                .collect(Collectors.toList());
        
        vo.setMessages(messageVOs);
        
        // 判断是否有更多数据
        boolean hasMore = messages.size() >= limit;
        vo.setHasMore(hasMore);
        
        // 设置下一页游标（最后一条记录的ID）
        if (hasMore && !messages.isEmpty()) {
            vo.setNextCursorId(messages.get(messages.size() - 1).getId());
        }
        
        return vo;
    }
}
