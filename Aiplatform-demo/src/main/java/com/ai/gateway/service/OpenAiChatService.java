package com.ai.gateway.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ai.gateway.common.Constants;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.ModelConfig;
import com.ai.gateway.entity.UserApiKey;
import com.ai.gateway.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiChatService {

    // DeepSeek 模型名称映射（已弃用 deepseek-chat 和 deepseek-reasoner）
    // 当前可用模型：deepseek-v4-flash, deepseek-v4-pro
    private static final Map<String, String> MODEL_NAME_MAPPING = Map.of(
        "deepseek-v4-flash", "deepseek-v4-flash",
        "deepseek-v4-pro", "deepseek-v4-pro"
    );

    private final ModelConfigService modelConfigService;
    private final CallLogService callLogService;
    private final BillingService billingService;
    private final UserApiKeyService userApiKeyService;
    private final FreeLimitService freeLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    @Qualifier("chatExecutor")
    private Executor chatExecutor;

    public String chat(String apiKey, Long userId, ChatRequest request, String requestId) {
        long startTime = System.currentTimeMillis();
        ChatConfig config = resolveChatConfig(request, userId);

        if ("free".equals(request.getMode())) {
            if (!freeLimitService.checkAndRecord(userId, config.model)) {
                Map<String, Integer> quota = freeLimitService.getUserFreeQuota(userId);
                throw new BusinessException("今日免费额度已用完（已用" + quota.get("usedCalls") + "/" + quota.get("maxCalls") + "次调用，" + quota.get("usedTokens") + "/" + quota.get("maxTokens") + " tokens）。请使用自定义模型或充值后使用付费模型，或等待明日重置");
            }
        } else if ("paid".equals(request.getMode())) {
            // 使用点数计费
            boolean ok = billingService.preDeductPoints(userId, requestId, request.getModel());
            log.info("付费模式点数预扣检查: userId={}, model={}, ok={}", userId, request.getModel(), ok);
            if (!ok) {
                BigDecimal currentPoints = billingService.getUserPoints(userId);
                BigDecimal pointsCost = billingService.calculatePointsCost(request.getModel());
                throw new BusinessException("点数不足（当前点数：" + currentPoints + "），请充值后使用付费模型或使用免费模式。需要点数：" + pointsCost);
            }
        }

        try {
            Map<String, Object> requestBody = buildRequestBody(request, config.format);

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
                // 使用点数结算
                billingService.settlePoints(userId, requestId, request.getModel());
                log.info("点数结算完成: userId={}, requestId={}, model={}", userId, requestId, request.getModel());
            }

            saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                       cost, System.currentTimeMillis() - startTime, true, null);

            log.info("对话成功: userId={}, model={}, inputTokens={}, outputTokens={}, cost={}, mode={}",
                    userId, request.getModel(), inputTokens, outputTokens, cost, request.getMode());

            return content;

        } catch (Exception e) {
            log.error("对话失败: userId={}, model={}", userId, request.getModel(), e);
            if ("paid".equals(request.getMode())) {
                billingService.settleBalance(userId, requestId, BigDecimal.ZERO);
            }
            saveCallLog(apiKey, userId, request.getModel(), 0, 0, BigDecimal.ZERO,
                       System.currentTimeMillis() - startTime, false, e.getMessage());
            throw new RuntimeException("对话失败: " + e.getMessage(), e);
        }
    }

    public SseEmitter streamChat(String apiKey, Long userId, ChatRequest request, String requestId) {
        long startTime = System.currentTimeMillis();
        // 增加超时时间到120秒，支持更长的对话
        SseEmitter emitter = new SseEmitter(120000L);

        // 设置完成和错误回调，防止内存泄漏
        emitter.onCompletion(() -> {
            log.info("SSE连接完成: userId={}, requestId={}", userId, requestId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: userId={}, requestId={}", userId, requestId);
            if ("paid".equals(request.getMode())) {
                billingService.settleBalance(userId, requestId, BigDecimal.ZERO);
            }
        });
        
        emitter.onError((ex) -> {
            log.error("SSE连接错误: userId={}, requestId={}", userId, requestId, ex);
            if ("paid".equals(request.getMode())) {
                billingService.settleBalance(userId, requestId, BigDecimal.ZERO);
            }
        });

        ChatConfig config = resolveChatConfig(request, userId);

        if ("free".equals(request.getMode())) {
            if (!freeLimitService.checkAndRecord(userId, config.model)) {
                try {
                    Map<String, Integer> quota = freeLimitService.getUserFreeQuota(userId);
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "今日免费额度已用完（已用" + quota.get("usedCalls") + "/" + quota.get("maxCalls") + "次调用）。请使用付费模式或等待明日重置");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    emitter.complete();
                } catch (IOException ignored) {}
                return emitter;
            }
        } else if ("paid".equals(request.getMode())) {
            // 使用点数计费
            boolean ok = billingService.preDeductPoints(userId, requestId, request.getModel());
            log.info("付费模式点数预扣检查: userId={}, model={}, ok={}", userId, request.getModel(), ok);
            if (!ok) {
                try {
                    BigDecimal currentPoints = billingService.getUserPoints(userId);
                    BigDecimal pointsCost = billingService.calculatePointsCost(request.getModel());
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", "点数不足（当前点数：" + currentPoints + "），请充值后使用付费模型或使用免费模式。需要点数：" + pointsCost);
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    emitter.complete();
                } catch (IOException ignored) {}
                return emitter;
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> requestBody = buildRequestBody(request, config.format);
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
                    if ("paid".equals(request.getMode())) billingService.settleBalance(userId, requestId, BigDecimal.ZERO);
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
                    // 使用点数结算
                    billingService.settlePoints(userId, requestId, request.getModel());
                }

                saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                           cost, System.currentTimeMillis() - startTime, true, null);

                Map<String, Object> doneData = new HashMap<>();
                doneData.put("finished", true);
                doneData.put("inputTokens", inputTokens);
                doneData.put("outputTokens", outputTokens);
                doneData.put("cost", cost);
                emitter.send(SseEmitter.event().name("done").data(doneData));
                emitter.complete();

            } catch (Exception e) {
                log.error("流式对话失败: userId={}", userId, e);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", e.getMessage() != null ? e.getMessage() : "对话服务异常");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    if ("paid".equals(request.getMode())) billingService.settleBalance(userId, requestId, BigDecimal.ZERO);
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }, chatExecutor);

        return emitter;
    }

    private ChatConfig resolveChatConfig(ChatRequest request, Long userId) {
        String mode = request.getMode() != null ? request.getMode() : "free";

        switch (mode) {
            case "custom":
                if (request.getUserApiKeyId() != null) {
                    UserApiKey userKey = userApiKeyService.getKeyForUse(request.getUserApiKeyId(), userId);
                    String baseUrl = resolveCustomBaseUrl(userKey.getBaseUrl(), request.getModel());
                    String format = resolveProviderFormat(userKey.getProvider());
                    return new ChatConfig(userKey.getApiKey(), baseUrl, format, null, true, request.getModel());
                }
                if (request.getCustomApiKey() != null && !request.getCustomApiKey().isEmpty()) {
                    String baseUrl = request.getCustomBaseUrl();
                    if (baseUrl != null && !baseUrl.contains("/v1/") && !baseUrl.endsWith("/chat/completions")) {
                        baseUrl = baseUrl.replaceAll("/$", "") + "/v1/chat/completions";
                    }
                    String format = request.getCustomFormat() != null ? request.getCustomFormat() : "openai";
                    return new ChatConfig(request.getCustomApiKey(), baseUrl, format, null, true, request.getModel());
                }
                throw new BusinessException("请选择或输入自定义 API Key");

            case "paid":
                ModelConfig paidConfig = modelConfigService.validateModel(request.getModel());
                return new ChatConfig(paidConfig.getApiKey(), paidConfig.getBaseUrl(), "openai", paidConfig, false, request.getModel());

            case "free":
            default:
                ModelConfig freeConfig = modelConfigService.validateModel(request.getModel());
                return new ChatConfig(freeConfig.getApiKey(), freeConfig.getBaseUrl(), "openai", freeConfig, false, request.getModel());
        }
    }

    private String resolveCustomBaseUrl(String baseUrl, String model) {
        if (baseUrl == null) return "https://api.openai.com/v1/chat/completions";
        if (baseUrl.contains("/v1/") || baseUrl.endsWith("/chat/completions")) return baseUrl;
        return baseUrl.replaceAll("/$", "") + "/v1/chat/completions";
    }

    private String resolveProviderFormat(String provider) {
        return "anthropic".equalsIgnoreCase(provider) ? "anthropic" : "openai";
    }

    private BigDecimal resolveCost(ChatConfig config, int inputTokens, int outputTokens) {
        if (config.isCustom || config.modelConfig == null) return BigDecimal.ZERO;
        return modelConfigService.calculateCost(config.modelConfig, inputTokens, outputTokens);
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, String format) {
        Map<String, Object> requestBody = new HashMap<>();
        String actualModel = MODEL_NAME_MAPPING.getOrDefault(request.getModel(), request.getModel());
        requestBody.put("model", actualModel);
        requestBody.put("messages", request.getMessages());

        if (request.getMaxTokens() != null) {
            requestBody.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null && !"anthropic".equalsIgnoreCase(format)) {
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
        final ModelConfig modelConfig;
        final boolean isCustom;
        final String model;

        ChatConfig(String apiKey, String baseUrl, String format, ModelConfig modelConfig, boolean isCustom, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.format = format;
            this.modelConfig = modelConfig;
            this.isCustom = isCustom;
            this.model = model;
        }
    }
}
