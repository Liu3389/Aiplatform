package com.ai.gateway.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ai.gateway.common.Constants;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class OpenAiChatService {

    private static final String SYSTEM_PROMPT = "你是一个智能助手。在回答代码、技术文档或复杂逻辑时，请务必使用 Markdown 格式（包括代码块和 Mermaid 流程图）。对于简单的日常对话或简短问答，请直接自然回复，无需强制使用 Markdown。";

    private final PlatformModelService platformModelService;
    private final CallLogService callLogService;
    private final PointsService pointsService;
    private final FreeLimitService freeLimitService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("chatExecutor")
    private Executor chatExecutor;

    public OpenAiChatService(PlatformModelService platformModelService,
                             CallLogService callLogService,
                             PointsService pointsService,
                             FreeLimitService freeLimitService,
                             StringRedisTemplate redisTemplate) {
        this.platformModelService = platformModelService;
        this.callLogService = callLogService;
        this.pointsService = pointsService;
        this.freeLimitService = freeLimitService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 非流式对话（修复 P2-16: 移除 apiKey 和 requestId 参数）
     */
    public String chat(Long userId, ChatRequest request) {
        // 生成内部 requestId 用于计费追踪
        String requestId = "req_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String apiKey = null; // 平台模式不再需要用户传入 API Key
        long startTime = System.currentTimeMillis();
        ChatConfig config = resolveChatConfig(request, userId);

        // 修复 P0-2: 恢复 free/paid 模式判断，但移除已废弃的 custom 模式
        if ("free".equals(request.getMode())) {
            if (!freeLimitService.checkAndRecord(userId, config.model)) {
                Map<String, Integer> quota = freeLimitService.getUserFreeQuota(userId);
                throw new BusinessException("今日点数额度已用完（已用" + quota.get("usedCalls") + "/" + quota.get("maxCalls") + "次调用，" + quota.get("usedTokens") + "/" + quota.get("maxTokens") + " tokens）。请充值点数或等待明日重置");
            }
        } else if ("paid".equals(request.getMode())) {
            boolean ok = pointsService.preDeductPoints(userId, requestId, request.getModel());
            log.info("付费模式点数预扣: userId={}, model={}, ok={}", userId, request.getModel(), ok);
            if (!ok) {
                BigDecimal currentPoints = pointsService.getUserPoints(userId);
                throw new BusinessException("点数不足（当前点数：" + currentPoints + "），请充值点数或使用每日赠送的点数");
            }
        }

        try {
            // 修复 P0-1: 将 config 传入 buildRequestBody，确保使用 actualModel
            Map<String, Object> requestBody = buildRequestBody(request, config);

            HttpResponse response = HttpRequest.post(config.baseUrl)
                    .header("Authorization", "Bearer " + config.apiKey)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(requestBody))
                    .timeout(30000)
                    .execute();

            if (!response.isOk()) {
                throw new RuntimeException("API调用失败: HTTP " + response.getStatus());
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            String content = rootNode.path("choices").get(0).path("message").path("content").asText();
            int inputTokens = rootNode.path("usage").path("prompt_tokens").asInt();
            int outputTokens = rootNode.path("usage").path("completion_tokens").asInt();

            BigDecimal cost = resolveCost(config, inputTokens, outputTokens);

            if ("free".equals(request.getMode())) {
                freeLimitService.addTokens(userId, inputTokens + outputTokens);
            } else if ("paid".equals(request.getMode())) {
                // 修复 P0-1: 统一使用模型配置的固定点数进行结算，不再依赖本地估算
                BigDecimal pointsCost = platformModelService.getModelPointsCost(config.modelConfig);
                pointsService.settlePoints(userId, requestId, pointsCost, request.getModel());
                log.info("点数结算完成: userId={}, requestId={}, model={}, points={}", userId, requestId, request.getModel(), pointsCost);
            }

            saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                       cost, System.currentTimeMillis() - startTime, true, null);

            log.info("对话成功: userId={}, model={}, inputTokens={}, outputTokens={}, cost={}, mode={}",
                    userId, request.getModel(), inputTokens, outputTokens, cost, request.getMode());

            return content;

        } catch (Exception e) {
            log.error("对话失败: userId={}, model={}", userId, request.getModel(), e);
            if ("paid".equals(request.getMode())) {
                pointsService.rollbackPreDeduct(userId, requestId);
                log.info("点数预扣已回滚: userId={}, requestId={}", userId, requestId);
            }
            saveCallLog(apiKey, userId, request.getModel(), 0, 0, BigDecimal.ZERO,
                       System.currentTimeMillis() - startTime, false, e.getMessage());
            throw new RuntimeException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式对话（修复 P2-16: 移除 apiKey 和 requestId 参数）
     */
    public SseEmitter streamChat(Long userId, ChatRequest request) {
        // 生成内部 requestId 用于计费追踪
        String requestId = "req_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        String apiKey = null; // 平台模式不再需要用户传入 API Key
        long startTime = System.currentTimeMillis();
        // 增加超时时间到120秒，支持更长的对话
        SseEmitter emitter = new SseEmitter(120000L);
        // 修复 P1-2: 使用原子标志位防止超时后异步线程继续执行
        java.util.concurrent.atomic.AtomicBoolean isCompleted = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 设置完成和错误回调，防止内存泄漏
        emitter.onCompletion(() -> {
            isCompleted.set(true);
            log.info("SSE连接完成: userId={}, requestId={}", userId, requestId);
        });
        
        emitter.onTimeout(() -> {
            if (isCompleted.compareAndSet(false, true)) {
                log.warn("SSE连接超时: userId={}, requestId={}", userId, requestId);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "对话响应超时，请稍后重试");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                } catch (IOException e) {
                    log.error("发送超时错误事件失败", e);
                }
                if ("paid".equals(request.getMode())) {
                    pointsService.rollbackPreDeduct(userId, requestId);
                }
            }
        });
        
        emitter.onError((ex) -> {
            if (isCompleted.compareAndSet(false, true)) {
                log.error("SSE连接错误: userId={}, requestId={}", userId, requestId, ex);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "对话连接发生异常");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                } catch (IOException e) {
                    log.error("发送错误事件失败", e);
                }
                if ("paid".equals(request.getMode())) {
                    pointsService.rollbackPreDeduct(userId, requestId);
                }
            }
        });

        ChatConfig config = resolveChatConfig(request, userId);

        if ("free".equals(request.getMode())) {
            if (!freeLimitService.checkAndRecord(userId, config.model)) {
                try {
                    Map<String, Integer> quota = freeLimitService.getUserFreeQuota(userId);
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "今日点数额度已用完（已用" + quota.get("usedCalls") + "/" + quota.get("maxCalls") + "次调用）。请充值点数或等待明日重置");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    emitter.complete();
                } catch (IOException ignored) {}
                return emitter;
            }
        } else if ("paid".equals(request.getMode())) {
            boolean ok = pointsService.preDeductPoints(userId, requestId, request.getModel());
            log.info("流式付费模式点数预扣: userId={}, model={}, ok={}", userId, request.getModel(), ok);
            if (!ok) {
                try {
                    BigDecimal currentPoints = pointsService.getUserPoints(userId);
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "点数不足（当前点数：" + currentPoints + "），请充值点数或使用每日赠送的点数");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    emitter.complete();
                } catch (IOException ignored) {}
                return emitter;
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                // 修复 P0-1: 流式请求同样使用 actualModel
                Map<String, Object> requestBody = buildRequestBody(request, config);
                requestBody.put("stream", true);

                HttpResponse response = HttpRequest.post(config.baseUrl)
                        .header("Authorization", "Bearer " + config.apiKey)
                        .header("Content-Type", "application/json")
                        .body(objectMapper.writeValueAsString(requestBody))
                        .timeout(60000)
                        .executeAsync();

                if (!response.isOk()) {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "API调用失败: HTTP " + response.getStatus());
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    if ("paid".equals(request.getMode())) redisTemplate.delete("points:pre_deduct:" + requestId);
                    emitter.complete();
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8));

                StringBuilder fullContent = new StringBuilder();
                int inputTokens = 0;
                int outputTokens = 0;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) break;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String content = node.path("choices").get(0).path("delta").path("content").asText();
                            if (content != null && !content.isEmpty()) {
                                fullContent.append(content);
                                Map<String, Object> eventData = new HashMap<>();
                                eventData.put("content", content);
                                emitter.send(SseEmitter.event().name("message").data(eventData));
                            }
                        } catch (Exception e) {
                            log.warn("解析流式数据失败", e);
                        }
                    }
                }

                // 更准确的token估算方法
                String promptText = request.getMessages().stream()
                        .map(msg -> msg.getContent() != null ? msg.getContent() : "")
                        .reduce("", String::concat);
                inputTokens = estimateTokens(promptText);
                outputTokens = estimateTokens(fullContent.toString());

                BigDecimal cost = resolveCost(config, inputTokens, outputTokens);

                if ("free".equals(request.getMode())) {
                    freeLimitService.addTokens(userId, inputTokens + outputTokens);
                } else if ("paid".equals(request.getMode())) {
                    // 修复 P0-1: 流式对话同样采用固定点数结算
                    BigDecimal pointsCost = platformModelService.getModelPointsCost(config.modelConfig);
                    pointsService.settlePoints(userId, requestId, pointsCost, request.getModel());
                }

                saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                           cost, System.currentTimeMillis() - startTime, true, null);

                Map<String, Object> doneData = new HashMap<>();
                doneData.put("finished", true);
                doneData.put("inputTokens", inputTokens);
                doneData.put("outputTokens", outputTokens);
                doneData.put("cost", cost);
                emitter.send(SseEmitter.event().name("done").data(doneData));
                isCompleted.set(true); // 标记正常完成
                emitter.complete();

            } catch (Exception e) {
                log.error("流式对话失败: userId={}", userId, e);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", e.getMessage() != null ? e.getMessage() : "对话服务异常");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    if ("paid".equals(request.getMode())) pointsService.rollbackPreDeduct(userId, requestId);
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }, chatExecutor);

        return emitter;
    }

    private ChatConfig resolveChatConfig(ChatRequest request, Long userId) {
        String modelName = request.getModel();
        
        // 修复 P0-4: 从数据库读取映射配置
        PlatformModelConfig config = platformModelService.getByDisplayName(modelName);
        if (config == null || config.getStatus() != 1) {
            throw new BusinessException("模型不存在或已禁用: " + modelName);
        }

        return new ChatConfig(
                config.getApiKey(), 
                config.getBaseUrl(), 
                resolveProviderFormat(config.getProvider()), 
                config, 
                false, 
                config.getActualModel()
        );
    }

    private String resolveProviderFormat(String provider) {
        return "anthropic".equalsIgnoreCase(provider) ? "anthropic" : "openai";
    }

    private BigDecimal resolveCost(ChatConfig config, int inputTokens, int outputTokens) {
        // 修复 P0-2: 使用 PlatformModelService 计算点数消耗
        if (config.isCustom || config.modelConfig == null) return BigDecimal.ZERO;
        return platformModelService.getModelPointsCost(config.modelConfig);
    }

    /**
     * 构建请求体（修复 P0-1: 直接使用 ChatConfig 中的实际模型名）
     */
    private Map<String, Object> buildRequestBody(ChatRequest request, ChatConfig config) {
        Map<String, Object> requestBody = new HashMap<>();
        // 关键修复：使用 config.model (actualModel) 而不是 request.getModel() (displayName)
        requestBody.put("model", config.model);
        
        List<ChatRequest.ChatMessage> messages = new ArrayList<>(request.getMessages());
        boolean hasSystemMessage = messages.stream().anyMatch(msg -> "system".equals(msg.getRole()));
        if (!hasSystemMessage) {
            ChatRequest.ChatMessage systemMsg = new ChatRequest.ChatMessage();
            systemMsg.setRole("system");
            systemMsg.setContent(SYSTEM_PROMPT);
            messages.add(0, systemMsg);
        }
        requestBody.put("messages", messages);

        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null && !"anthropic".equalsIgnoreCase(config.format)) {
            requestBody.put("temperature", request.getTemperature());
        }
        return requestBody;
    }

    private void saveCallLog(String apiKey, Long userId, String model, int inputTokens,
                             int outputTokens, BigDecimal cost, long duration,
                             boolean success, String errorMessage) {
        CallLog callLog = new CallLog();
        callLog.setApiKey(apiKey);
        callLog.setUserId(userId);
        callLog.setModel(model);
        callLog.setInputTokens(inputTokens);
        callLog.setOutputTokens(outputTokens);
        callLog.setCost(cost);
        callLog.setDuration((int) duration);
        callLog.setStatus(success ? Constants.CALL_STATUS_SUCCESS : Constants.CALL_STATUS_FAILED);
        callLog.setErrorMessage(errorMessage);
        callLogService.saveCallLog(callLog);
    }

    /**
     * 估算文本的token数量
     * 使用更精确的估算方法：英文单词按空格分割，中文按字符计算
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // 简单但相对准确的估算：
        // - 英文单词平均1.3 tokens per word
        // - 中文字符约1 token per character
        // - 混合文本取平均值
        
        int englishWords = 0;
        int chineseChars = 0;
        int otherChars = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c) && c < 128) {
                englishWords++;
            } else if (c >= 0x4E00 && c <= 0x9FFF) { // CJK统一汉字
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        
        // 英文单词数（通过空格分割估算）
        String[] words = text.split("\\s+");
        int wordCount = words.length;
        
        // 综合估算：英文单词 * 1.3 + 中文字符 * 1 + 其他字符 * 0.5
        return (int) Math.ceil(wordCount * 1.3 + chineseChars + otherChars * 0.5);
    }

    private static class ChatConfig {
        final String apiKey;
        final String baseUrl;
        final String format;
        final PlatformModelConfig modelConfig;
        final boolean isCustom;
        final String model;

        ChatConfig(String apiKey, String baseUrl, String format, PlatformModelConfig modelConfig, boolean isCustom, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.format = format;
            this.modelConfig = modelConfig;
            this.isCustom = isCustom;
            this.model = model;
        }
    }
}
