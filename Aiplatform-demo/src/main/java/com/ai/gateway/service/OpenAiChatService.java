package com.ai.gateway.service;

import com.ai.gateway.common.Constants;
import com.ai.gateway.common.ResultCode;
import com.ai.gateway.config.SpringAiClientFactory;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.entity.CallLog;
import com.ai.gateway.entity.AiChargeIntent;
import com.ai.gateway.entity.PackageTemplate;
import com.ai.gateway.entity.PlatformModelConfig;
import com.ai.gateway.entity.UserSubscription;
import com.ai.gateway.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class OpenAiChatService {

    private static final String SYSTEM_PROMPT = "你是一个智能助手。在回答代码、技术文档或复杂逻辑时，请务必使用 Markdown 格式（包括代码块和 Mermaid 流程图）。" +
            "输出 Markdown 时必须遵循 CommonMark/GFM：标题标记 # 与标题文字之间必须有空格，标题、列表、表格和代码块之间保留必要的空行；代码块使用成对的 ``` 包围并完整闭合，不能在内容中途省略或截断。" +
            "对于简单的日常对话或简短问答，请直接自然回复，无需强制使用 Markdown。";
    private static final String DEFAULT_VISION_MODEL = "deepseek-v4-flash-vision-exp";
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);

    private final PlatformModelService platformModelService;
    private final CallLogService callLogService;
    private final PointsService pointsService;
    private final StringRedisTemplate redisTemplate;
    private final ChatMessageService chatMessageService;
    private final com.ai.gateway.mapper.ConversationMapper conversationMapper;
    private final UserSubscriptionService userSubscriptionService;
    private final com.ai.gateway.mapper.PackageTemplateMapper packageTemplateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OssService ossService;
    private final WebClient webClient = WebClient.builder().build();

    @Autowired
    @Qualifier("chatExecutor")
    private Executor chatExecutor;

    @Autowired
    private SearxngSearchService searxngSearchService;

    @Autowired
    private SearchIntentService searchIntentService;

    @Autowired
    private BillingSettlementCoordinator billingSettlementCoordinator;

    @Autowired
    private SpringAiClientFactory springAiClientFactory;

    public OpenAiChatService(PlatformModelService platformModelService,
                             CallLogService callLogService,
                             PointsService pointsService,
                             StringRedisTemplate redisTemplate,
                             ChatMessageService chatMessageService,
                             com.ai.gateway.mapper.ConversationMapper conversationMapper,
                             UserSubscriptionService userSubscriptionService,
                             com.ai.gateway.mapper.PackageTemplateMapper packageTemplateMapper,
                             OssService ossService) {
        this.platformModelService = platformModelService;
        this.callLogService = callLogService;
        this.pointsService = pointsService;
        this.redisTemplate = redisTemplate;
        this.chatMessageService = chatMessageService;
        this.conversationMapper = conversationMapper;
        this.userSubscriptionService = userSubscriptionService;
        this.packageTemplateMapper = packageTemplateMapper;
        this.ossService = ossService;
    }

    /**
     * 非流式对话（修复 P2-16: 移除 apiKey 和 requestId 参数）
     */
    public String chat(Long userId, ChatRequest request) {
        normalizeChatMode(request);
        validateConversationOwnership(userId, request);
        String requestId = resolveRequestId(userId, request);
        String requestFingerprint = calculateRequestFingerprint(request);
        String apiKey = null; // 平台模式不再需要用户传入 API Key
        long startTime = System.currentTimeMillis();
        ChatConfig config = resolveChatConfig(request, userId);

        // 模型权限校验
        validateModelAccess(userId, config.modelConfig);

        // 图片请求自动切换到已配置的视觉模型，兼容旧客户端和未同步视觉模型选择器的页面。
        config = resolveVisionConfigIfNeeded(request, userId, config);

        // Token限制检查（后台静默检查，不告诉用户）
        validateTokenLimits(userId, request, config);

        // 平台对话统一从点数余额预扣；旧的 free 模式不再允许绕过点数流水。
        BigDecimal pointsCost = platformModelService.getModelPointsCost(config.modelConfig);
        if (!billingSettlementCoordinator.prepareReservation(userId, requestId, pointsCost,
                request.getModel(), requestFingerprint)) {
            String replay = replayCompletedRequest(userId, requestId, requestFingerprint);
            if (replay != null) return replay;
            throw new IllegalStateException("相同幂等键的请求已在处理中或无法恢复，请勿重复提交");
        }
        boolean ok = pointsService.preDeductPoints(userId, requestId, request.getModel());
        log.info("平台对话点数预扣: userId={}, model={}, ok={}", userId, request.getModel(), ok);
        if (!ok) {
            billingSettlementCoordinator.rejectReservation(userId, requestId, "点数不足或预扣失败");
            BigDecimal currentPoints = pointsService.getUserPoints(userId);
            throw new BusinessException("点数不足（当前点数：" + currentPoints + "），请充值点数或使用每日赠送的点数");
        }
        if (!billingSettlementCoordinator.markReserved(requestId)
                || !billingSettlementCoordinator.markUpstreamStarted(requestId)) {
            billingSettlementCoordinator.refundAfterFailure(userId, requestId, "无法持久化上游调用状态");
            throw new IllegalStateException("点数请求状态更新失败，请稍后重试");
        }

        try {
            String content;
            int inputTokens;
            int outputTokens;
            // adapter 按 provider 和消息类型选择 Spring AI、OpenAI 兼容直连或 Anthropic 原生协议。
            ProviderResponse providerResponse = callProviderAdapter(request, config);
            if (!billingSettlementCoordinator.markResponseReceived(requestId, pointsCost)) {
                throw new IllegalStateException("模型已响应，但无法持久化结算恢复状态");
            }
            content = providerResponse.content;
            inputTokens = providerResponse.inputTokens;
            outputTokens = providerResponse.outputTokens;

            log.debug("非流式对话Token统计: userId={}, model={}, inputTokens={}, outputTokens={}, mode=actual",
                    userId, request.getModel(), inputTokens, outputTokens);

            BigDecimal cost = resolveCost(config, inputTokens, outputTokens);

            // 保存对话消息到数据库
            Long conversationId = getOrCreateConversation(userId, request);
            saveChatMessages(requestId, conversationId, userId, request, content,
                    request.getModel(), inputTokens + outputTokens, startTime);

            saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                       cost, System.currentTimeMillis() - startTime, true, null);

            // 固定点数不依赖本地 token 估算。outbox 提交后，Rabbit 或本地扫描器负责完成结算。
            if (!billingSettlementCoordinator.settle(userId, requestId, pointsCost, request.getModel())) {
                throw new IllegalStateException("点数结算意图保存失败，请稍后重试");
            }
            log.info("点数结算意图已保存: userId={}, requestId={}, model={}, points={}", userId, requestId, request.getModel(), pointsCost);

            log.info("对话成功: userId={}, model={}, inputTokens={}, outputTokens={}, cost={}, mode={}",
                    userId, request.getModel(), inputTokens, outputTokens, cost, request.getMode());

            return content;

        } catch (BusinessException e) {
            if (ok) {
                billingSettlementCoordinator.refundAfterFailure(userId, requestId, e.getMessage());
                log.info("业务异常，点数请求进入恢复处理: userId={}, requestId={}", userId, requestId);
            }
            saveCallLog(apiKey, userId, request.getModel(), 0, 0, BigDecimal.ZERO,
                    System.currentTimeMillis() - startTime, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("对话失败: userId={}, model={}", userId, request.getModel(), e);
            billingSettlementCoordinator.refundAfterFailure(userId, requestId, e.getMessage());
            log.info("点数请求已交给恢复状态机: userId={}, requestId={}", userId, requestId);
            saveCallLog(apiKey, userId, request.getModel(), 0, 0, BigDecimal.ZERO,
                       System.currentTimeMillis() - startTime, false, e.getMessage());
            throw new RuntimeException("对话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式对话（修复 P2-16: 移除 apiKey 和 requestId 参数）
     */
    public SseEmitter streamChat(Long userId, ChatRequest request) {
        normalizeChatMode(request);
        return streamChatWithEmitter(userId, request, null, null);
    }

    /**
     * 流式对话（支持传入自定义 SseEmitter）
     * 用于文件对话等场景，可以复用同一个 emitter
     */
    public SseEmitter streamChatWithEmitter(Long userId, ChatRequest request, String requestId, SseEmitter customEmitter) {
        normalizeChatMode(request);
        final String effectiveRequestId = requestId == null || requestId.isBlank()
                ? resolveRequestId(userId, request) : requestId;
        final String requestFingerprint = calculateRequestFingerprint(request);
        String apiKey = null; // 平台模式不再需要用户传入 API Key
        long startTime = System.currentTimeMillis();
        // 使用自定义 emitter 或创建新的。文件对话也必须注册同一套生命周期回调，
        // 否则用户在读取文件/模型输出时关闭页面，预扣点数没有可靠的回滚入口。
        SseEmitter emitter = customEmitter != null ? customEmitter : new SseEmitter(180000L);
        java.util.concurrent.atomic.AtomicBoolean terminal = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean preDeducted = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean settled = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean rollbackStarted = new java.util.concurrent.atomic.AtomicBoolean(false);

        Runnable rollbackPreDeduct = () -> {
            if (!preDeducted.get() || settled.get() || !rollbackStarted.compareAndSet(false, true)) {
                return;
            }
            billingSettlementCoordinator.handleClientDisconnect(userId, effectiveRequestId);
            log.info("流式对话断开已交给点数恢复状态机: userId={}, requestId={}", userId, effectiveRequestId);
        };

        // 无论是普通对话还是文件对话，都以 emitter 生命周期作为最后一道兜底。
        // terminal 防止异步模型线程在断开后继续写入；settled 防止“已结算后断开”被误回滚。
        emitter.onCompletion(() -> {
            if (terminal.compareAndSet(false, true)) {
                rollbackPreDeduct.run();
                log.warn("SSE连接在终态事件前完成: userId={}, requestId={}", userId, effectiveRequestId);
            } else {
                log.info("SSE连接完成: userId={}, requestId={}", userId, effectiveRequestId);
            }
        });

        emitter.onTimeout(() -> {
            if (terminal.compareAndSet(false, true)) {
                rollbackPreDeduct.run();
                log.warn("SSE连接超时: userId={}, requestId={}", userId, effectiveRequestId);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "对话响应超时，请稍后重试")));
                } catch (IOException e) {
                    log.debug("发送超时错误事件失败（连接可能已断开）: {}", e.getMessage());
                }
                emitter.complete();
            }
        });

        emitter.onError(ex -> {
            if (terminal.compareAndSet(false, true)) {
                rollbackPreDeduct.run();
                log.error("SSE连接错误: userId={}, requestId={}", userId, effectiveRequestId, ex);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "对话连接发生异常")));
                } catch (IOException e) {
                    log.debug("发送错误事件失败（连接可能已断开）: {}", e.getMessage());
                }
            }
        });

        try {
            validateConversationOwnership(userId, request);
        } catch (BusinessException e) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", e.getMessage())));
            } catch (IOException ioEx) {
                log.debug("发送会话归属错误时IO异常: {}", ioEx.getMessage());
            }
            terminal.set(true);
            emitter.complete();
            return emitter;
        }

        ChatConfig resolvedConfig = resolveChatConfig(request, userId);

        // 模型权限校验
        try {
            validateModelAccess(userId, resolvedConfig.modelConfig);
        } catch (BusinessException e) {
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errData));
                terminal.set(true);
                emitter.complete();
            } catch (IOException ioEx) {
                log.debug("发送模型权限错误事件时IO异常: {}", ioEx.getMessage());
            }
            return emitter;
        }

        try {
            // 图片请求自动切换到已配置的视觉模型，保证文件对话和普通对话使用同一能力判断。
            resolvedConfig = resolveVisionConfigIfNeeded(request, userId, resolvedConfig);
        } catch (BusinessException e) {
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errData));
                terminal.set(true);
                emitter.complete();
            } catch (IOException ioEx) {
                log.debug("发送图片模型错误事件时IO异常: {}", ioEx.getMessage());
            }
            return emitter;
        }

        final ChatConfig config = resolvedConfig;

        // Token限制检查（后台静默检查，不告诉用户）
        try {
            validateTokenLimits(userId, request, config);
        } catch (BusinessException e) {
            try {
                Map<String, Object> errData = new HashMap<>();
                errData.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errData));
                terminal.set(true);
                emitter.complete();
            } catch (IOException ioEx) {
                log.debug("发送Token限制错误事件时IO异常: {}", ioEx.getMessage());
            }
            return emitter;
        }

        // 平台对话统一从点数余额预扣，旧的 free 模式不再允许绕过点数流水。
        BigDecimal pointsCost = platformModelService.getModelPointsCost(config.modelConfig);
        if (!billingSettlementCoordinator.prepareReservation(userId, effectiveRequestId, pointsCost,
                request.getModel(), requestFingerprint)) {
            try {
                String replay = replayCompletedRequest(userId, effectiveRequestId, requestFingerprint);
                if (replay != null) {
                    com.ai.gateway.entity.ChatMessage assistant = chatMessageService.findByRequestIdAndRole(
                            effectiveRequestId, "assistant");
                    emitter.send(SseEmitter.event().name("message").data(Map.of("content", replay)));
                    Map<String, Object> doneData = new HashMap<>();
                    doneData.put("finished", true);
                    doneData.put("inputTokens", 0);
                    doneData.put("outputTokens", assistant == null || assistant.getTokens() == null ? 0 : assistant.getTokens());
                    doneData.put("cost", BigDecimal.ZERO);
                    doneData.put("pointsConsumed", pointsCost);
                    doneData.put("conversationId", assistant == null ? request.getConversationId() : assistant.getConversationId());
                    emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
                    settled.set(true);
                    terminal.set(true);
                    emitter.complete();
                    return emitter;
                }
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "相同幂等键的请求已在处理中或无法恢复，请勿重复提交")));
                terminal.set(true);
                emitter.complete();
            } catch (Exception ioEx) {
                log.info("幂等聊天请求无法重放: requestId={}, reason={}", effectiveRequestId, ioEx.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", ioEx.getMessage() == null ? "重复请求无法恢复" : ioEx.getMessage())));
                } catch (IOException sendError) {
                    log.debug("发送幂等请求错误事件失败: {}", sendError.getMessage());
                }
                terminal.set(true);
                emitter.complete();
            }
            return emitter;
        }
        boolean ok = pointsService.preDeductPoints(userId, effectiveRequestId, request.getModel());
        log.info("流式平台对话点数预扣: userId={}, model={}, ok={}", userId, request.getModel(), ok);
        if (!ok) {
            billingSettlementCoordinator.rejectReservation(userId, effectiveRequestId, "点数不足或预扣失败");
            try {
                BigDecimal currentPoints = pointsService.getUserPoints(userId);
                Map<String, Object> errData = new HashMap<>();
                errData.put("message", "点数不足（当前点数：" + currentPoints + "），请充值点数或使用每日赠送的点数");
                emitter.send(SseEmitter.event().name("error").data(errData));
                terminal.set(true);
                emitter.complete();
            } catch (IOException ioEx) {
                log.debug("发送点数不足错误事件时IO异常: {}", ioEx.getMessage());
            }
            return emitter;
        }
        preDeducted.set(true);
        if (!billingSettlementCoordinator.markReserved(effectiveRequestId)
                || !billingSettlementCoordinator.markUpstreamStarted(effectiveRequestId)) {
            billingSettlementCoordinator.refundAfterFailure(userId, effectiveRequestId, "无法持久化上游调用状态");
            preDeducted.set(false);
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", "点数请求状态更新失败，请重试")));
                terminal.set(true);
                emitter.complete();
            } catch (IOException ioEx) {
                log.debug("发送点数请求状态错误时IO异常: {}", ioEx.getMessage());
            }
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // ===== 搜索工具集成（类似豆包的联网搜索） =====
                // 1. 获取最后一条用户消息，判断是否需要联网搜索
                String lastUserMessage = request.getMessages().stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .reduce((first, second) -> second)
                        .map(ChatRequest.ChatMessage::getTextContent)
                        .orElse("");

                if (searxngSearchService.isEnabled() && !lastUserMessage.isEmpty()
                        && !Boolean.TRUE.equals(request.getDisableSearch())
                        && searchIntentService.needsSearch(lastUserMessage)) {
                    String searchQuery = searchIntentService.extractSearchQuery(lastUserMessage);
                    String usedEngine = searxngSearchService.resolveEngine(request.getSearchEngine());

                    // 2. 发送搜索中状态事件（前端展示"正在搜索..."，带引擎名）
                    try {
                        Map<String, Object> searchData = new HashMap<>();
                        searchData.put("stage", "searching");
                        searchData.put("query", searchQuery);
                        searchData.put("engine", usedEngine);
                        emitter.send(SseEmitter.event().name("search").data(searchData));
                    } catch (IOException e) {
                        log.debug("发送搜索状态事件失败: {}", e.getMessage());
                    }

                    // 3. 执行搜索（per-request 引擎：auto/searxng/jina/bing/baidu）
                    List<SearxngSearchService.SearchResult> searchResults =
                            searxngSearchService.search(searchQuery, 5, request.getSearchEngine());

                    // 4. 发送搜索结果事件 + 注入上下文到 prompt
                    if (!searchResults.isEmpty()) {
                        try {
                            Map<String, Object> resultsData = new HashMap<>();
                            resultsData.put("stage", "results");
                            resultsData.put("query", searchQuery);
                            resultsData.put("engine", searchResults.get(0).getSource().isBlank()
                                    ? usedEngine : searchResults.get(0).getSource());
                            resultsData.put("results", searchResults);
                            emitter.send(SseEmitter.event().name("search").data(resultsData));
                        } catch (IOException e) {
                            log.debug("发送搜索结果事件失败: {}", e.getMessage());
                        }
                        // 把搜索结果注入到 messages（在最后一条 user 消息前插入 system 消息）
                        injectSearchContext(request, searxngSearchService.buildSearchContext(searchResults));
                    } else {
                        try {
                            Map<String, Object> emptyData = new HashMap<>();
                            emptyData.put("stage", "empty");
                            emptyData.put("query", searchQuery);
                            emptyData.put("engine", usedEngine);
                            emitter.send(SseEmitter.event().name("search").data(emptyData));
                        } catch (IOException e) {
                            log.debug("发送无搜索结果状态失败: {}", e.getMessage());
                        }
                    }
                }

                StringBuilder fullContent = new StringBuilder();
                int inputTokens = 0;
                int outputTokens = 0;

                // 按厂商协议分别解析上游 SSE，再统一汇总为本平台的 message 事件和 usage。
                ProviderStreamResponse providerResponse =
                        streamProviderAdapter(request, config, emitter, terminal);
                fullContent.append(providerResponse.content);
                inputTokens = providerResponse.inputTokens;
                outputTokens = providerResponse.outputTokens;
                String fullResponseText = fullContent.toString();
                if (!fullResponseText.isBlank()
                        && !billingSettlementCoordinator.markResponseReceived(effectiveRequestId, pointsCost)) {
                    throw new IllegalStateException("模型已响应，但无法持久化结算恢复状态");
                }
                if (terminal.get()) {
                    return;
                }
                if (fullResponseText.isBlank()) {
                    // 不能把上游空响应当作成功，否则前端只会得到空 assistant
                    // 消息，点数也会被正常结算，用户无法判断发生了什么。
                    throw new IllegalStateException("模型返回空响应，请重试");
                }

                // 流式未返回 usage 时使用估算值（计费使用固定点数，估算仅用于统计分析）
                if (inputTokens == 0) {
                    inputTokens = estimateTokens(request.getMessages().stream()
                            .map(ChatRequest.ChatMessage::getTextContent)
                            .reduce("", String::concat));
                }
                if (outputTokens == 0) {
                    outputTokens = estimateTokens(fullResponseText);
                }

                log.debug("流式对话Token统计: userId={}, inputTokens={}, outputTokens={}, mode=estimated",
                        userId, inputTokens, outputTokens);

                BigDecimal cost = resolveCost(config, inputTokens, outputTokens);

                // 保存对话消息到数据库
                Long conversationId = getOrCreateConversation(userId, request);
                saveChatMessages(effectiveRequestId, conversationId, userId, request, fullResponseText,
                        request.getModel(), inputTokens + outputTokens, startTime);

                saveCallLog(apiKey, userId, request.getModel(), inputTokens, outputTokens,
                           cost, System.currentTimeMillis() - startTime, true, null);

                // done 只在聊天内容已持久化、结算意图已进入 outbox 后发送。
                BigDecimal pointsConsumed = pointsCost;
                if (!billingSettlementCoordinator.settle(userId, effectiveRequestId, pointsConsumed, request.getModel())) {
                    throw new IllegalStateException("点数结算意图保存失败，请稍后重试");
                }
                settled.set(true);

                // 使用objectMapper确保JSON格式严格一致
                Map<String, Object> doneData = new HashMap<>();
                doneData.put("finished", true);
                doneData.put("inputTokens", inputTokens);
                doneData.put("outputTokens", outputTokens);
                doneData.put("cost", cost);
                doneData.put("pointsConsumed", pointsConsumed);
                doneData.put("conversationId", conversationId);
                String doneJson = objectMapper.writeValueAsString(doneData);
                emitter.send(SseEmitter.event().name("done").data(doneJson));
                terminal.set(true); // 只有明确发送 done 后才标记正常完成
                emitter.complete();

            } catch (Exception e) {
                if (!terminal.compareAndSet(false, true)) {
                    return;
                }
                log.error("流式对话失败: userId={}, requestId={}", userId, effectiveRequestId, e);
                try {
                    Map<String, Object> errData = new HashMap<>();
                    errData.put("message", e.getMessage() != null ? e.getMessage() : "对话服务异常");
                    emitter.send(SseEmitter.event().name("error").data(errData));
                } catch (IOException ioEx) {
                    log.debug("发送错误事件时IO异常（可能客户端已断开）: {}", ioEx.getMessage());
                } finally {
                    billingSettlementCoordinator.refundAfterFailure(userId, effectiveRequestId, e.getMessage());
                }
                emitter.completeWithError(e);
            } finally {
                // Spring AI 内部管理 HTTP 连接，无需手动关闭
            }
        }, chatExecutor);

        return emitter;
    }

    /**
     * 平台聊天统一走点数模式。兼容旧客户端的 mode 字段，但不再允许调用方选择免费分支。
     */
    private void normalizeChatMode(ChatRequest request) {
        request.setMode("paid");
    }

    private String resolveRequestId(Long userId, ChatRequest request) {
        String key = request.getIdempotencyKey();
        if (key == null || key.isBlank()) {
            return "req_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (key.length() > 128) {
            throw new BusinessException("幂等键长度不能超过 128 个字符");
        }
        return "req_" + sha256(userId + ":" + key).substring(0, 60);
    }

    private String calculateRequestFingerprint(ChatRequest request) {
        Map<String, Object> requestShape = new LinkedHashMap<>();
        requestShape.put("model", request.getModel());
        requestShape.put("conversationId", request.getConversationId());
        requestShape.put("messages", request.getMessages());
        requestShape.put("fileUrls", request.getFileUrls());
        requestShape.put("files", request.getFiles());
        requestShape.put("searchEngine", request.getSearchEngine());
        requestShape.put("disableSearch", request.getDisableSearch());
        try {
            return sha256(objectMapper.writeValueAsString(requestShape));
        } catch (Exception e) {
            throw new BusinessException("无法计算聊天请求幂等摘要");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    /** Replays a durable completion for the same key and refuses in-flight or mismatched reuse. */
    private String replayCompletedRequest(Long userId, String requestId, String requestFingerprint) {
        com.ai.gateway.entity.AiChargeIntent intent = billingSettlementCoordinator.getIntent(requestId);
        if (intent == null) return null;
        if (!Objects.equals(intent.getUserId(), userId)
                || !Objects.equals(intent.getRequestFingerprint(), requestFingerprint)) {
            throw new BusinessException("幂等键已用于不同的聊天请求");
        }

        String status = intent.getStatus();
        if (AiChargeIntent.RESPONSE_RECEIVED.equals(status)) {
            com.ai.gateway.entity.ChatMessage persisted = chatMessageService.findByRequestIdAndRole(requestId, "assistant");
            if (persisted != null && billingSettlementCoordinator.markMessagesPersisted(requestId)) {
                status = AiChargeIntent.MESSAGES_PERSISTED;
            }
        }
        if (!AiChargeIntent.MESSAGES_PERSISTED.equals(status)
                && !AiChargeIntent.SETTLEMENT_PENDING.equals(status)
                && !AiChargeIntent.SETTLED.equals(status)) {
            throw new BusinessException("相同幂等键的请求已在处理中或已退款，请勿重复提交");
        }

        com.ai.gateway.entity.ChatMessage assistant = chatMessageService.findByRequestIdAndRole(requestId, "assistant");
        if (assistant == null || assistant.getContent() == null || assistant.getContent().isBlank()) {
            throw new BusinessException("聊天回复已记录但无法读取，点数请求已进入恢复处理");
        }
        if (!billingSettlementCoordinator.settle(userId, requestId, intent.getActualPoints(), intent.getModelName())) {
            throw new BusinessException("聊天回复已保存，点数结算正在恢复，请稍后重试");
        }
        return assistant.getContent();
    }

    private ChatConfig resolveChatConfig(ChatRequest request, Long userId) {
        String modelName = request.getModel();
        
        // 修复 P0-4: 从数据库读取映射配置
        PlatformModelConfig requestedConfig = platformModelService.getByDisplayName(modelName);
        if (requestedConfig == null || requestedConfig.getStatus() != 1) {
            throw new BusinessException("模型不存在或已禁用: " + modelName);
        }

        String apiKey = platformModelService.resolveApiKey(requestedConfig);

        // 展示模型、权限、点数定价和实际调用线路必须来自同一条配置。
        // 未填写真实上游时快速失败，不能把 GPT/Claude 等模型悄悄伪装成 DeepSeek。
        if (requestedConfig.getBaseUrl() == null || requestedConfig.getBaseUrl().isBlank()
                || apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("模型上游线路未配置，请联系管理员");
        }

        return new ChatConfig(
                apiKey,
                requestedConfig.getBaseUrl(),
                resolveProviderFormat(requestedConfig.getProvider()),
                requestedConfig,
                false, 
                resolveUpstreamModel(requestedConfig.getProvider(), requestedConfig.getActualModel())
        );
    }

    /**
     * DeepSeek 已将 V4 Flash 的旧展示别名迁移到统一的 deepseek-flash 模型 ID。
     * 展示名称仍保留产品命名，但发给上游的 model 必须使用当前可调用的 ID。
     */
    private String resolveUpstreamModel(String provider, String actualModel) {
        if ("deepseek".equalsIgnoreCase(provider)
                && ("deepseek-v4-flash".equalsIgnoreCase(actualModel)
                || DEFAULT_VISION_MODEL.equalsIgnoreCase(actualModel))) {
            return "deepseek-flash";
        }
        return actualModel;
    }

    private String resolveProviderFormat(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException("模型厂商未配置");
        }
        // Anthropic 使用自己的 Messages API；其余厂商必须提供 OpenAI 兼容接口地址。
        return "anthropic".equalsIgnoreCase(provider) ? "anthropic" : "openai";
    }

    private BigDecimal resolveCost(ChatConfig config, int inputTokens, int outputTokens) {
        // 修复 P0-2: 使用 PlatformModelService 计算点数消耗
        if (config.isCustom || config.modelConfig == null) return BigDecimal.ZERO;
        return platformModelService.getModelPointsCost(config.modelConfig);
    }

    /**
     * 把搜索结果上下文注入到对话消息中（在最后一条 user 消息前插入 system 消息）
     * 使 AI 能基于搜索结果回答问题，类似豆包的联网搜索
     */
    private void injectSearchContext(ChatRequest request, String searchContext) {
        List<ChatRequest.ChatMessage> messages = new ArrayList<>(request.getMessages());
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastUserIdx = i;
                break;
            }
        }
        ChatRequest.ChatMessage searchMsg = new ChatRequest.ChatMessage();
        searchMsg.setRole("system");
        searchMsg.setContent(searchContext);
        if (lastUserIdx >= 0) {
            messages.add(lastUserIdx, searchMsg);
        } else {
            messages.add(searchMsg);
        }
        request.setMessages(messages);
    }

    /**
     * 构建 OpenAI 兼容请求体。
     * 文本、图片和模型切换都从这里进入，保证真实发给上游的模型名始终来自
     * ChatConfig，而不是前端可能已经变化的展示状态。
     */
    Map<String, Object> buildOpenAiPayload(ChatRequest request, ChatConfig config, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        boolean hasSystemMessage = request.getMessages().stream()
                .anyMatch(msg -> "system".equals(msg.getRole()));
        if (!hasSystemMessage) {
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        }
        for (ChatRequest.ChatMessage message : request.getMessages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole() == null ? "user" : message.getRole());
            item.put("content", convertOpenAiContent(message));
            messages.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model);
        payload.put("messages", messages);
        payload.put("stream", stream);
        payload.put("max_tokens", resolveOutputTokenLimit(request, config));
        if (request.getTemperature() != null && !"anthropic".equalsIgnoreCase(config.format)) {
            payload.put("temperature", request.getTemperature());
        }
        if (stream) payload.put("stream_options", Map.of("include_usage", true));
        return payload;
    }

    private Object convertOpenAiContent(ChatRequest.ChatMessage message) {
        if (!(message.getContent() instanceof List<?> rawParts)) {
            return message.getTextContent();
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        for (Object rawPart : rawParts) {
            if (!(rawPart instanceof Map<?, ?> rawMap)) continue;
            String type = rawMap.get("type") == null ? "" : String.valueOf(rawMap.get("type"));
            if ("text".equals(type) || "input_text".equals(type) || "output_text".equals(type)) {
                Object text = rawMap.get("text");
                if (text != null) parts.add(Map.of("type", "text", "text", String.valueOf(text)));
            } else if ("image_url".equals(type) || "input_image".equals(type)) {
                Object rawUrl = rawMap.get("image_url");
                if (rawUrl instanceof Map<?, ?> imageMap && imageMap.get("url") != null) {
                    Map<String, Object> imageUrl = new LinkedHashMap<>();
                    imageUrl.put("url", prepareImageUrl(String.valueOf(imageMap.get("url"))));
                    if (imageMap.get("detail") != null) {
                        imageUrl.put("detail", String.valueOf(imageMap.get("detail")));
                    }
                    parts.add(Map.of("type", "image_url", "image_url", imageUrl));
                } else if (rawMap.get("url") != null) {
                    parts.add(Map.of("type", "image_url", "image_url",
                            Map.of("url", prepareImageUrl(String.valueOf(rawMap.get("url"))))));
                }
            } else if ("file".equals(type) && rawMap.get("file_data") != null) {
                Map<String, Object> filePart = new LinkedHashMap<>();
                filePart.put("type", "file");
                filePart.put("file_data", String.valueOf(rawMap.get("file_data")));
                if (rawMap.get("filename") != null) {
                    filePart.put("filename", String.valueOf(rawMap.get("filename")));
                }
                parts.add(filePart);
            }
        }
        return parts.isEmpty() ? message.getTextContent() : parts;
    }

    private String extractMessageText(JsonNode message) {
        String content = extractNodeText(message == null ? null : message.path("content"));
        if (!content.isBlank()) return content;
        return extractNodeText(message == null ? null : message.path("reasoning_content"));
    }

    private String extractNodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode part : node) {
                String text = extractNodeText(part.path("text"));
                if (text.isBlank()) text = extractNodeText(part.path("content"));
                if (text.isBlank() && part.isTextual()) text = part.asText();
                result.append(text);
            }
            return result.toString();
        }
        return "";
    }

    private String resolveMediaType(String url) {
        String lower = url.toLowerCase();
        if (lower.startsWith("data:image/jpeg") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.startsWith("data:image/gif") || lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.startsWith("data:image/webp") || lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    /**
     * Spring AI 0.8.1 的 OpenAI 消息转换器会丢弃 Media，只保留文本。
     * 带图片的 OpenAI 兼容请求因此直接调用 provider API，避免“前端上传成功但模型只收到文字”的假成功。
     */
    private boolean requiresDirectVisionCall(ChatRequest request, ChatConfig config) {
        return hasImageMessage(request) && "openai".equalsIgnoreCase(config.format);
    }

    /**
     * 协议适配入口：把厂商差异收敛在上游请求边界，聊天生命周期、计费和消息持久化保持共用。
     */
    private ProviderResponse callProviderAdapter(ChatRequest request, ChatConfig config) {
        if ("anthropic".equalsIgnoreCase(config.format)) return callAnthropic(request, config);
        if (isDeepSeekConfig(config)) return callDeepSeekResponses(request, config);
        // Spring AI 负责普通 OpenAI 兼容文本请求；图片消息走原生 WebClient，
        // 因为当前 Spring AI 0.8.1 消息转换器会丢失部分 Media 数据。
        return requiresDirectVisionCall(request, config)
                ? callOpenAiCompatible(request, config)
                : callSpringAiOpenAiCompatible(request, config);
    }

    private ProviderStreamResponse streamProviderAdapter(ChatRequest request, ChatConfig config,
                                                          SseEmitter emitter,
                                                          java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        return "anthropic".equalsIgnoreCase(config.format)
                ? streamAnthropic(request, config, emitter, isCompleted)
                : isDeepSeekConfig(config)
                ? streamDeepSeekResponses(request, config, emitter, isCompleted)
                : streamOpenAiCompatible(request, config, emitter, isCompleted);
    }

    private boolean isDeepSeekConfig(ChatConfig config) {
        return config != null && config.modelConfig != null
                && "deepseek".equalsIgnoreCase(config.modelConfig.getProvider());
    }

    private ProviderResponse callSpringAiOpenAiCompatible(ChatRequest request, ChatConfig config) {
        try {
            List<Message> messages = new ArrayList<>();
            if (request.getMessages().stream().noneMatch(m -> "system".equals(m.getRole()))) {
                messages.add(new SystemMessage(SYSTEM_PROMPT));
            }
            for (ChatRequest.ChatMessage message : request.getMessages()) {
                String text = message.getTextContent();
                String role = message.getRole() == null ? "user" : message.getRole();
                switch (role) {
                    case "system" -> messages.add(new SystemMessage(text));
                    case "assistant" -> messages.add(new AssistantMessage(text));
                    default -> messages.add(new UserMessage(text));
                }
            }

            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().withModel(config.model);
            options.withMaxTokens(resolveOutputTokenLimit(request, config));
            if (request.getTemperature() != null) options.withTemperature(request.getTemperature().floatValue());
            OpenAiChatClient client = springAiClientFactory.getChatModel(config.baseUrl, config.apiKey);
            ChatResponse response = client.call(new Prompt(messages, options.build()));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("模型返回空响应");
            }
            String content = response.getResult().getOutput().getContent();
            if (content == null || content.isBlank()) throw new IllegalStateException("模型返回空响应");
            int inputTokens = 0;
            int outputTokens = 0;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Long promptTokens = response.getMetadata().getUsage().getPromptTokens();
                Long generationTokens = response.getMetadata().getUsage().getGenerationTokens();
                inputTokens = promptTokens == null ? 0 : promptTokens.intValue();
                outputTokens = generationTokens == null ? 0 : generationTokens.intValue();
            }
            return new ProviderResponse(content, inputTokens, outputTokens);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() == null ? "模型调用失败" : e.getMessage(), e);
        }
    }

    private ProviderResponse callOpenAiCompatible(ChatRequest request, ChatConfig config) {
        try {
            String responseBody = webClient.post()
                    .uri(resolveChatCompletionUrl(config.baseUrl))
                    .headers(headers -> {
                        headers.setBearerAuth(config.apiKey);
                        headers.set(org.springframework.http.HttpHeaders.ACCEPT, org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    })
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(buildOpenAiPayload(request, config, false))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                if (response.statusCode().isError()) {
                                    throw new IllegalStateException("模型调用失败: " + extractProviderError(body));
                                }
                                return body;
                            }))
                    .block(PROVIDER_TIMEOUT);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("模型返回空响应");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error")) {
                throw new IllegalStateException("模型调用失败: " + extractProviderError(responseBody));
            }
            String content = extractMessageText(root.path("choices").path(0).path("message"));
            if (content.isBlank()) {
                throw new IllegalStateException("模型返回空响应");
            }
            JsonNode usage = root.path("usage");
            return new ProviderResponse(
                    content,
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0)
            );
        } catch (Exception e) {
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new RuntimeException(e.getMessage() == null ? "模型调用失败" : e.getMessage(), e);
        }
    }

    /**
     * DeepSeek V4 当前优先使用 Responses API。旧 Chat Completions 线路在部分账号上
     * 只返回保活帧而不结束，Responses API 提供明确的 completed/failed 终态，适合平台
     * 的统一流式生命周期和点数结算。
     */
    private ProviderResponse callDeepSeekResponses(ChatRequest request, ChatConfig config) {
        try {
            String responseBody = webClient.post()
                    .uri(resolveDeepSeekResponsesUrl(config.baseUrl))
                    .headers(headers -> headers.setBearerAuth(config.apiKey))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(buildDeepSeekResponsesPayload(request, config, false))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                if (response.statusCode().isError()) {
                                    throw new IllegalStateException("模型调用失败: " + extractProviderError(body));
                                }
                                return body;
                            }))
                    .block(PROVIDER_TIMEOUT);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("模型返回空响应");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error") && !root.path("error").isNull()) {
                throw new IllegalStateException("模型调用失败: " + extractProviderError(responseBody));
            }
            String content = extractResponsesOutputText(root);
            if (content.isBlank()) {
                throw new IllegalStateException("模型返回空响应");
            }
            JsonNode usage = root.path("usage");
            return new ProviderResponse(
                    content,
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0)
            );
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) throw businessException;
            throw new RuntimeException(e.getMessage() == null ? "DeepSeek 模型调用失败" : e.getMessage(), e);
        }
    }

    Map<String, Object> buildDeepSeekResponsesPayload(ChatRequest request, ChatConfig config, boolean stream) {
        List<String> instructions = new ArrayList<>();
        List<Map<String, Object>> input = new ArrayList<>();
        for (ChatRequest.ChatMessage message : request.getMessages()) {
            String role = message.getRole() == null ? "user" : message.getRole();
            if ("system".equals(role)) {
                String text = message.getTextContent();
                if (text != null && !text.isBlank()) instructions.add(text);
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "message");
            item.put("role", "assistant".equals(role) ? "assistant" : "user");
            item.put("content", convertDeepSeekResponsesContent(message, role));
            input.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model);
        payload.put("instructions", instructions.isEmpty() ? SYSTEM_PROMPT : String.join("\n\n", instructions));
        payload.put("input", input);
        payload.put("stream", stream);
        payload.put("store", false);
        // 平台默认关闭隐藏推理，避免简单问答长时间停留在“思考中”；需要推理的模型
        // 后续可以把该选项接到对话设置，但不能让默认请求无限等待。
        payload.put("reasoning", Map.of("effort", "none"));
        payload.put("max_output_tokens", resolveOutputTokenLimit(request, config));
        if (request.getTemperature() != null) payload.put("temperature", request.getTemperature());
        return payload;
    }

    private Object convertDeepSeekResponsesContent(ChatRequest.ChatMessage message, String role) {
        if (!(message.getContent() instanceof List<?> rawParts)) {
            return message.getTextContent();
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        for (Object rawPart : rawParts) {
            if (!(rawPart instanceof Map<?, ?> rawMap)) continue;
            String type = rawMap.get("type") == null ? "" : String.valueOf(rawMap.get("type"));
            if ("text".equals(type) || "input_text".equals(type) || "output_text".equals(type)) {
                Object text = rawMap.get("text");
                if (text != null) {
                    Map<String, Object> textPart = new LinkedHashMap<>();
                    textPart.put("type", "assistant".equals(role) ? "output_text" : "input_text");
                    textPart.put("text", String.valueOf(text));
                    parts.add(textPart);
                }
            } else if ("image_url".equals(type) || "input_image".equals(type)) {
                Object rawUrl = rawMap.get("image_url");
                String url = rawUrl instanceof Map<?, ?> imageMap && imageMap.get("url") != null
                        ? String.valueOf(imageMap.get("url"))
                        : String.valueOf(rawUrl);
                if ("null".equals(url) && rawMap.get("url") != null) url = String.valueOf(rawMap.get("url"));
                if (url == null || url.isBlank() || "null".equals(url)) continue;

                Map<String, Object> imagePart = new LinkedHashMap<>();
                imagePart.put("type", "input_image");
                imagePart.put("image_url", prepareImageUrl(url));
                if (rawUrl instanceof Map<?, ?> imageMap && imageMap.get("detail") != null) {
                    imagePart.put("detail", String.valueOf(imageMap.get("detail")));
                }
                parts.add(imagePart);
            } else if ("file".equals(type) && rawMap.get("file_data") != null) {
                Map<String, Object> filePart = new LinkedHashMap<>();
                filePart.put("type", "input_file");
                filePart.put("file_data", String.valueOf(rawMap.get("file_data")));
                if (rawMap.get("filename") != null) filePart.put("filename", String.valueOf(rawMap.get("filename")));
                parts.add(filePart);
            }
        }
        return parts.isEmpty() ? message.getTextContent() : parts;
    }

    private ProviderStreamResponse streamDeepSeekResponses(ChatRequest request, ChatConfig config,
                                                            SseEmitter emitter,
                                                            java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        DeepSeekResponsesStreamAccumulator accumulator = new DeepSeekResponsesStreamAccumulator();
        try {
            webClient.post()
                    .uri(resolveDeepSeekResponsesUrl(config.baseUrl))
                    .headers(headers -> headers.setBearerAuth(config.apiKey))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(buildDeepSeekResponsesPayload(request, config, true))
                    .exchangeToFlux(response -> {
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMapMany(body -> Flux.error(new IllegalStateException(
                                            "模型调用失败: " + extractProviderError(body))));
                        }
                        return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                        });
                    })
                    .doOnNext(event -> consumeDeepSeekResponsesEvent(event, accumulator, emitter, isCompleted))
                    .blockLast(PROVIDER_TIMEOUT);

            flushDeepSeekResponsesSseBuffer(accumulator, emitter, isCompleted);
            // 部分代理会在最后一个 data 帧后吞掉 SSE 的空行或 response.completed 帧，
            // 但 response.output_text.done 已经证明正文完整结束。不能把这种正常收尾误判成断流。
            if (!accumulator.providerCompleted && accumulator.textCompleted && !accumulator.content.isEmpty()) {
                accumulator.providerCompleted = true;
            }
            if (!accumulator.providerCompleted) {
                throw new IllegalStateException("模型响应连接已中断，请稍后重试");
            }
            return new ProviderStreamResponse(
                    accumulator.content.toString(),
                    accumulator.inputTokens,
                    accumulator.outputTokens
            );
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new RuntimeException(e.getMessage() == null ? "DeepSeek 模型流式调用失败" : e.getMessage(), e);
        }
    }

    private void consumeDeepSeekResponsesEvent(ServerSentEvent<String> event,
                                               DeepSeekResponsesStreamAccumulator accumulator,
                                               SseEmitter emitter,
                                               java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (event == null || event.data() == null || event.data().isBlank()) return;
        String data = event.data().trim();
        if ("[DONE]".equals(data)) return;
        accumulator.eventName = event.event() == null ? "" : event.event();
        accumulator.dataLines.clear();
        accumulator.dataLines.add(data);
        dispatchDeepSeekResponsesEvent(accumulator, emitter, isCompleted);
    }

    private void consumeDeepSeekResponsesChunk(String chunk, DeepSeekResponsesStreamAccumulator accumulator,
                                               SseEmitter emitter,
                                               java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (chunk == null || chunk.isEmpty()) return;
        accumulator.sseBuffer.append(chunk);
        int lineEnd;
        while ((lineEnd = accumulator.sseBuffer.indexOf("\n")) >= 0) {
            String line = accumulator.sseBuffer.substring(0, lineEnd).replace("\r", "");
            accumulator.sseBuffer.delete(0, lineEnd + 1);
            consumeDeepSeekResponsesLine(line, accumulator, emitter, isCompleted);
        }
    }

    private void consumeDeepSeekResponsesLine(String line, DeepSeekResponsesStreamAccumulator accumulator,
                                              SseEmitter emitter,
                                              java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (line.isEmpty()) {
            dispatchDeepSeekResponsesEvent(accumulator, emitter, isCompleted);
            return;
        }
        if (line.startsWith(":")) return;
        if (line.startsWith("event:")) {
            accumulator.eventName = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
            accumulator.dataLines.add(line.substring("data:".length()).replaceFirst("^\\s", ""));
        }
    }

    private void dispatchDeepSeekResponsesEvent(DeepSeekResponsesStreamAccumulator accumulator,
                                                SseEmitter emitter,
                                                java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        String payload = String.join("\n", accumulator.dataLines).trim();
        String eventName = accumulator.eventName;
        accumulator.eventName = "";
        accumulator.dataLines.clear();
        if (payload.isEmpty()) return;

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText(eventName == null ? "" : eventName);
            if ("response.output_text.delta".equals(type)) {
                String text = root.path("delta").asText("");
                if (!text.isEmpty()) {
                    accumulator.content.append(text);
                    if (!isCompleted.get()) emitter.send(SseEmitter.event().name("message").data(Map.of("content", text)));
                }
            } else if ("response.output_text.done".equals(type) && accumulator.content.isEmpty()) {
                String text = root.path("text").asText("");
                if (!text.isEmpty()) {
                    accumulator.content.append(text);
                    if (!isCompleted.get()) emitter.send(SseEmitter.event().name("message").data(Map.of("content", text)));
                }
                accumulator.textCompleted = true;
            } else if ("response.output_text.done".equals(type)) {
                accumulator.textCompleted = true;
            } else if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                JsonNode response = root.path("response");
                JsonNode usage = response.path("usage");
                accumulator.inputTokens = usage.path("input_tokens").asInt(accumulator.inputTokens);
                accumulator.outputTokens = usage.path("output_tokens").asInt(accumulator.outputTokens);
                if (accumulator.content.isEmpty()) accumulator.content.append(extractResponsesOutputText(response));
                accumulator.providerCompleted = true;
            } else if ("response.failed".equals(type)) {
                JsonNode responseError = root.path("response").path("error");
                String message = responseError.path("message").asText("");
                if (message.isBlank()) message = root.path("error").path("message").asText("");
                throw new IllegalStateException("模型调用失败: " + (message.isBlank() ? "上游响应失败" : message));
            }
        } catch (IOException e) {
            throw new RuntimeException("解析 DeepSeek Responses SSE 失败", e);
        }
    }

    private void flushDeepSeekResponsesSseBuffer(DeepSeekResponsesStreamAccumulator accumulator,
                                                 SseEmitter emitter,
                                                 java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (accumulator.sseBuffer.length() > 0) {
            String remainder = accumulator.sseBuffer.toString();
            accumulator.sseBuffer.setLength(0);
            consumeDeepSeekResponsesLine(remainder.replace("\r", ""), accumulator, emitter, isCompleted);
        }
        dispatchDeepSeekResponsesEvent(accumulator, emitter, isCompleted);
    }

    private String extractResponsesOutputText(JsonNode response) {
        JsonNode output = response == null ? null : response.path("output");
        if (output == null || !output.isArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode part : content) {
                String type = part.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    result.append(part.path("text").asText(""));
                }
            }
        }
        return result.toString();
    }

    private String resolveDeepSeekResponsesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("模型 API 地址未配置");
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/chat/completions")) url = url.substring(0, url.length() - "/chat/completions".length());
        if (url.endsWith("/v1")) url = url.substring(0, url.length() - "/v1".length());
        return url + "/responses";
    }

    /** Anthropic 原生 Messages API 适配；响应仍转换为平台统一的文本和 token 统计结构。 */
    private ProviderResponse callAnthropic(ChatRequest request, ChatConfig config) {
        try {
            String responseBody = webClient.post()
                    .uri(resolveAnthropicMessagesUrl(config.baseUrl))
                    .headers(headers -> {
                        headers.set("x-api-key", config.apiKey);
                        headers.set("anthropic-version", "2023-06-01");
                        headers.set(org.springframework.http.HttpHeaders.ACCEPT,
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    })
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(buildAnthropicPayload(request, config, false))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                if (response.statusCode().isError()) {
                                    throw new IllegalStateException("模型调用失败: " + extractProviderError(body));
                                }
                                return body;
                            }))
                    .block(PROVIDER_TIMEOUT);

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("模型返回空响应");
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error")) {
                throw new IllegalStateException("模型调用失败: " + extractProviderError(responseBody));
            }
            String content = extractNodeText(root.path("content"));
            if (content.isBlank()) throw new IllegalStateException("模型返回空响应");
            JsonNode usage = root.path("usage");
            return new ProviderResponse(content,
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0));
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) throw businessException;
            throw new RuntimeException(e.getMessage() == null ? "Anthropic 模型调用失败" : e.getMessage(), e);
        }
    }

    /**
     * Anthropic 的 SSE 事件名和 OpenAI 的 data 帧不同；在适配层转换为平台统一的 message 事件。
     */
    private ProviderStreamResponse streamAnthropic(ChatRequest request, ChatConfig config,
                                                   SseEmitter emitter,
                                                   java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        AnthropicStreamAccumulator accumulator = new AnthropicStreamAccumulator();
        try {
            webClient.post()
                    .uri(resolveAnthropicMessagesUrl(config.baseUrl))
                    .headers(headers -> {
                        headers.set("x-api-key", config.apiKey);
                        headers.set("anthropic-version", "2023-06-01");
                    })
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(buildAnthropicPayload(request, config, true))
                    .exchangeToFlux(response -> {
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class).defaultIfEmpty("")
                                    .flatMapMany(body -> Flux.error(new IllegalStateException(
                                            "模型调用失败: " + extractProviderError(body))));
                        }
                        return response.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
                    })
                    .doOnNext(event -> consumeAnthropicEvent(event, accumulator, emitter, isCompleted))
                    .blockLast(PROVIDER_TIMEOUT);
            return new ProviderStreamResponse(accumulator.content.toString(),
                    accumulator.inputTokens, accumulator.outputTokens);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new RuntimeException(e.getMessage() == null ? "Anthropic 流式调用失败" : e.getMessage(), e);
        }
    }

    private void consumeAnthropicEvent(ServerSentEvent<String> event, AnthropicStreamAccumulator accumulator,
                                       SseEmitter emitter,
                                       java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (event == null || event.data() == null || event.data().isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(event.data());
            String type = root.path("type").asText(event.event() == null ? "" : event.event());
            if ("error".equals(type)) {
                throw new IllegalStateException("模型调用失败: " + extractProviderError(event.data()));
            }
            JsonNode usage = "message_start".equals(type)
                    ? root.path("message").path("usage") : root.path("usage");
            if (usage.isObject()) {
                accumulator.inputTokens = usage.path("input_tokens").asInt(accumulator.inputTokens);
                accumulator.outputTokens = usage.path("output_tokens").asInt(accumulator.outputTokens);
            }
            JsonNode delta = root.path("delta");
            if ("text_delta".equals(delta.path("type").asText())) {
                String text = delta.path("text").asText("");
                if (!text.isEmpty()) {
                    accumulator.content.append(text);
                    if (!isCompleted.get()) emitter.send(SseEmitter.event().name("message").data(Map.of("content", text)));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解析 Anthropic SSE 事件失败", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new RuntimeException("处理 Anthropic SSE 事件失败", e);
        }
    }

    Map<String, Object> buildAnthropicPayload(ChatRequest request, ChatConfig config, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        List<String> systemParts = new ArrayList<>();
        for (ChatRequest.ChatMessage message : request.getMessages()) {
            String role = message.getRole() == null ? "user" : message.getRole();
            if ("system".equals(role)) {
                systemParts.add(message.getTextContent());
            } else {
                messages.add(Map.of("role", "assistant".equals(role) ? "assistant" : "user",
                        "content", convertAnthropicContent(message)));
            }
        }
        if (systemParts.isEmpty()) systemParts.add(SYSTEM_PROMPT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model);
        payload.put("system", String.join("\n\n", systemParts));
        payload.put("messages", messages);
        // Anthropic 要求 max_tokens；所有 provider 共用正整数策略，再映射到各自协议字段。
        payload.put("max_tokens", resolveOutputTokenLimit(request, config));
        // Anthropic 不同代际模型对 temperature 的支持不同；使用模型默认值避免新模型因非默认值拒绝请求。
        payload.put("stream", stream);
        return payload;
    }

    private Object convertAnthropicContent(ChatRequest.ChatMessage message) {
        if (!(message.getContent() instanceof List<?> rawParts)) return message.getTextContent();
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Object rawPart : rawParts) {
            if (!(rawPart instanceof Map<?, ?> rawMap)) continue;
            String type = rawMap.get("type") == null ? "" : String.valueOf(rawMap.get("type"));
            if ("text".equals(type) || "input_text".equals(type) || "output_text".equals(type)) {
                Object text = rawMap.get("text");
                if (text != null) parts.add(Map.of("type", "text", "text", String.valueOf(text)));
            } else if ("image_url".equals(type) || "input_image".equals(type)) {
                Object rawUrl = rawMap.get("image_url");
                String url = rawUrl instanceof Map<?, ?> imageMap && imageMap.get("url") != null
                        ? String.valueOf(imageMap.get("url")) : String.valueOf(rawUrl);
                if ("null".equals(url) && rawMap.get("url") != null) url = String.valueOf(rawMap.get("url"));
                String prepared = prepareImageUrl(url);
                if (prepared.startsWith("data:image/")) {
                    int comma = prepared.indexOf(',');
                    int semicolon = prepared.indexOf(';');
                    if (comma < 0 || semicolon < 0 || semicolon > comma) {
                        throw new IllegalArgumentException("Anthropic 图片 Base64 数据格式无效");
                    }
                    String mediaType = prepared.substring("data:".length(), semicolon);
                    String data = prepared.substring(comma + 1);
                    parts.add(Map.of("type", "image", "source", Map.of(
                            "type", "base64", "media_type", mediaType, "data", data)));
                } else if (prepared.startsWith("https://") || prepared.startsWith("http://")) {
                    // 公网图片 URL 由 Anthropic 获取，平台不在服务端自行抓取外部 URL。
                    parts.add(Map.of("type", "image", "source", Map.of("type", "url", "url", prepared)));
                } else {
                    throw new IllegalArgumentException("Anthropic 图片仅支持 Base64、HTTP(S) URL 或平台存储图片");
                }
            }
        }
        return parts.isEmpty() ? message.getTextContent() : parts;
    }

    private String resolveAnthropicMessagesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("模型 API 地址未配置");
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/v1/messages") || url.endsWith("/messages")) return url;
        if (url.endsWith("/v1")) return url + "/messages";
        return url + "/v1/messages";
    }

    /**
     * 直接消费 DeepSeek 的 SSE。开启 include_usage，拿到真实 token 后仍由统一点数结算链路处理。
     */
    private ProviderStreamResponse streamOpenAiCompatible(ChatRequest request, ChatConfig config,
                                                          SseEmitter emitter,
                                                          java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        ProviderStreamAccumulator accumulator = new ProviderStreamAccumulator();
        try {
            webClient.post()
                    .uri(resolveChatCompletionUrl(config.baseUrl))
                    .headers(headers -> headers.setBearerAuth(config.apiKey))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(buildOpenAiPayload(request, config, true))
                    .exchangeToFlux(response -> {
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMapMany(body -> Flux.error(new IllegalStateException(
                                            "模型调用失败: " + extractProviderError(body))));
                        }
                        return response.bodyToFlux(String.class);
                    })
                    .doOnNext(chunk -> consumeProviderSseChunk(chunk, accumulator, emitter, isCompleted))
                    .blockLast(PROVIDER_TIMEOUT);

            flushProviderSseBuffer(accumulator, emitter, isCompleted);
            if (accumulator.content.isEmpty() && accumulator.reasoning.length() > 0) {
                // 部分 DeepSeek 兼容网关只返回 reasoning_content，不返回 content。
                // 将其作为最后的可见答复，避免成功事件携带空消息。
                String fallback = accumulator.reasoning.toString();
                accumulator.content.append(fallback);
                if (!isCompleted.get()) {
                    emitter.send(SseEmitter.event().name("message").data(Map.of("content", fallback)));
                }
            }
            return new ProviderStreamResponse(
                    accumulator.content.toString(),
                    accumulator.inputTokens,
                    accumulator.outputTokens
            );
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e.getMessage() == null ? "模型流式调用失败" : e.getMessage(), e);
        }
    }

    private void consumeProviderSseChunk(String chunk, ProviderStreamAccumulator accumulator,
                                         SseEmitter emitter,
                                         java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (chunk == null || chunk.isEmpty()) return;
        accumulator.sseBuffer.append(chunk);
        int lineEnd;
        while ((lineEnd = accumulator.sseBuffer.indexOf("\n")) >= 0) {
            String line = accumulator.sseBuffer.substring(0, lineEnd).replace("\r", "");
            accumulator.sseBuffer.delete(0, lineEnd + 1);
            consumeProviderSseLine(line, accumulator, emitter, isCompleted);
        }

        // WebClient 的 StringDecoder 在部分响应头组合下会按 SSE 帧而不是换行分片，
        // 此时缓冲区里可能只有一个完整的 data: JSON 帧，不能等到流结束才处理。
        String buffered = accumulator.sseBuffer.toString().trim();
        if (buffered.startsWith("data:")) {
            int nextFrame = buffered.indexOf("data:", "data:".length());
            if (nextFrame > 0) {
                String frame = buffered.substring(0, nextFrame).trim();
                if (isCompleteProviderJsonFrame(frame)) {
                    String remaining = buffered.substring(nextFrame);
                    accumulator.sseBuffer.setLength(0);
                    consumeProviderSseLine(frame, accumulator, emitter, isCompleted);
                    consumeProviderSseChunk(remaining, accumulator, emitter, isCompleted);
                }
            } else if ("[DONE]".equals(buffered.substring("data:".length()).trim())
                    || isCompleteProviderJsonFrame(buffered)) {
                accumulator.sseBuffer.setLength(0);
                consumeProviderSseLine(buffered, accumulator, emitter, isCompleted);
            }
        } else if (isCompleteProviderJsonFrame(buffered)) {
            // 兼容某些代理剥离 SSE data: 前缀后再转发 JSON 的情况。
            accumulator.sseBuffer.setLength(0);
            consumeProviderSseLine("data: " + buffered, accumulator, emitter, isCompleted);
        }
    }

    private boolean isCompleteProviderJsonFrame(String frame) {
        String data = frame == null ? "" : frame.trim();
        if (data.startsWith("data:")) data = data.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return false;
        try {
            JsonNode root = objectMapper.readTree(data);
            return root != null && root.isObject();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void flushProviderSseBuffer(ProviderStreamAccumulator accumulator,
                                        SseEmitter emitter,
                                        java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        if (accumulator.sseBuffer.length() > 0) {
            String remainder = accumulator.sseBuffer.toString();
            accumulator.sseBuffer.setLength(0);
            consumeProviderSseChunk(remainder, accumulator, emitter, isCompleted);
        }
    }

    private void consumeProviderSseLine(String line, ProviderStreamAccumulator accumulator,
                                        SseEmitter emitter,
                                        java.util.concurrent.atomic.AtomicBoolean isCompleted) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) return;
        String data = trimmed.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) return;

        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                accumulator.inputTokens = usage.path("prompt_tokens").asInt(accumulator.inputTokens);
                accumulator.outputTokens = usage.path("completion_tokens").asInt(accumulator.outputTokens);
            }

            JsonNode delta = root.path("choices").path(0).path("delta");
            String content = extractNodeText(delta.path("content"));
            if (!content.isBlank()) {
                accumulator.content.append(content);
                if (!isCompleted.get()) {
                    emitter.send(SseEmitter.event().name("message").data(Map.of("content", content)));
                }
                return;
            }

            // DeepSeek 部分兼容线路会把推理阶段放在 reasoning_content，
            // 最终答案才放进 content。先缓存推理文本，只有最终没有 content
            // 时才作为可见兜底，避免把推理过程和正式答复混在一起。
            String reasoning = extractNodeText(delta.path("reasoning_content"));
            if (!reasoning.isBlank()) accumulator.reasoning.append(reasoning);
        } catch (IOException e) {
            throw new RuntimeException("发送模型响应失败", e);
        } catch (Exception e) {
            log.debug("忽略无法解析的模型 SSE 数据: {}", data, e);
        }
    }

    private String prepareImageUrl(String url) {
        if (url == null || url.isBlank() || url.startsWith("data:image/")) return url;
        String storageReference = ossService.toStorageReference(url);
        if (storageReference == null) return url;

        byte[] bytes = ossService.downloadFile(storageReference);
        String mimeType = resolveMediaType(url).toString();
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String resolveChatCompletionUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("模型 API 地址未配置");
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/v1/chat/completions") || url.endsWith("/chat/completions")) return url;
        if (url.endsWith("/v1")) return url + "/chat/completions";
        return url + "/v1/chat/completions";
    }

    private String extractProviderError(String body) {
        if (body == null || body.isBlank()) return "HTTP 请求失败";
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = root.path("error").path("message").asText("");
            return message.isBlank() ? body.substring(0, Math.min(body.length(), 300)) : message;
        } catch (Exception ignored) {
            return body.substring(0, Math.min(body.length(), 300));
        }
    }

    private static final class ProviderResponse {
        private final String content;
        private final int inputTokens;
        private final int outputTokens;

        private ProviderResponse(String content, int inputTokens, int outputTokens) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
    }

    private static final class ProviderStreamResponse {
        private final String content;
        private final int inputTokens;
        private final int outputTokens;

        private ProviderStreamResponse(String content, int inputTokens, int outputTokens) {
            this.content = content;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
    }

    private static final class ProviderStreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final StringBuilder sseBuffer = new StringBuilder();
        private int inputTokens;
        private int outputTokens;
    }

    private static final class DeepSeekResponsesStreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder sseBuffer = new StringBuilder();
        private final List<String> dataLines = new ArrayList<>();
        private String eventName = "";
        private int inputTokens;
        private int outputTokens;
        private boolean textCompleted;
        private boolean providerCompleted;
    }

    private static final class AnthropicStreamAccumulator {
        private final StringBuilder content = new StringBuilder();
        private int inputTokens;
        private int outputTokens;
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

    static class ChatConfig {
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

    int resolveOutputTokenLimit(ChatRequest request, ChatConfig config) {
        Integer modelCap = config == null || config.modelConfig == null
                ? null : config.modelConfig.getMaxOutputTokens();
        return ChatOutputTokenPolicy.resolve(request == null ? null : request.getMaxTokens(), modelCap, null);
    }

    private boolean hasImageMessage(ChatRequest request) {
        return request.getMessages().stream().anyMatch(ChatRequest.ChatMessage::hasImage);
    }

    private boolean supportsVision(ChatConfig config) {
        return config.modelConfig != null
                && config.modelConfig.getSupportsVision() != null
                && config.modelConfig.getSupportsVision() == 1;
    }

    /**
     * 图片请求不能继续使用纯文本模型。优先选择平台约定的 DeepSeek 视觉模型，
     * 找不到时再回退到其他已启用的视觉模型；最终仍由统一权限校验决定用户是否可用。
     */
    private ChatConfig resolveVisionConfigIfNeeded(ChatRequest request, Long userId, ChatConfig config) {
        // 图片统一使用已经验证可用的 DeepSeek 视觉线路。数据库里其他展示模型
        // 即使标记了 supports_vision，也可能还没有真实厂商地址，不能直接放行。
        if (!hasImageMessage(request)) {
            return config;
        }

        PlatformModelConfig visionModel = platformModelService.listEnabledModels().stream()
                .filter(this::isVisionModel)
                .sorted(Comparator.comparing((PlatformModelConfig model) ->
                                !DEFAULT_VISION_MODEL.equals(model.getActualModel()))
                        .thenComparing(model -> model.getSortOrder() == null ? Integer.MAX_VALUE : model.getSortOrder())
                        .thenComparing(PlatformModelConfig::getId))
                .findFirst()
                .orElse(null);

        if (visionModel == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前没有可用的视觉模型，暂时无法解析图片");
        }

        validateModelAccess(userId, visionModel);
        String requestedModel = request.getModel();
        request.setModel(visionModel.getActualModel());
        log.info("图片请求自动切换视觉模型: userId={}, requestedModel={}, visionModel={}",
                userId, requestedModel, visionModel.getActualModel());
        return new ChatConfig(
                visionModel.getApiKey(),
                visionModel.getBaseUrl(),
                resolveProviderFormat(visionModel.getProvider()),
                visionModel,
                false,
                resolveUpstreamModel(visionModel.getProvider(), visionModel.getActualModel())
        );
    }

    private boolean isVisionModel(PlatformModelConfig model) {
        return model != null
                && model.getSupportsVision() != null
                && model.getSupportsVision() == 1;
    }

    /**
     * Token限制检查（后台静默检查，不告诉用户具体限制）
     * 根据用户套餐限制单次输入和输出token数
     */
    private void validateTokenLimits(Long userId, ChatRequest request, ChatConfig config) {
        int maxInputTokens = 6000;
        int maxOutputTokens = 6000;
        try {
            // 1. 获取用户当前套餐
            com.ai.gateway.entity.UserSubscription subscription = userSubscriptionService.getCurrentSubscription(userId);
            if (subscription != null) {
                // 有套餐，使用套餐限制
                com.ai.gateway.entity.PackageTemplate pkg = packageTemplateMapper.selectById(subscription.getPackageId());
                if (pkg != null) {
                    maxInputTokens = pkg.getMaxInputTokens() != null ? pkg.getMaxInputTokens() : 0;
                    maxOutputTokens = pkg.getMaxOutputTokens() != null ? pkg.getMaxOutputTokens() : 0;
                }
            }

            // 2. 估算输入token数
            String promptText = request.getMessages().stream()
                    .map(ChatRequest.ChatMessage::getTextContent)
                    .reduce("", String::concat);
            int estimatedInputTokens = estimateTokens(promptText);

            // 3. 检查输入token限制
            if (maxInputTokens > 0 && estimatedInputTokens > maxInputTokens) {
                log.warn("输入Token超限: userId={}, estimated={}, limit={}", userId, estimatedInputTokens, maxInputTokens);
                throw new BusinessException("请求内容过长，请缩短输入内容");
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token套餐限制读取失败，使用保守免费额度: userId={}", userId, e);
        }

        Integer requested = request.getMaxTokens();
        Integer configuredModelCap = config == null || config.modelConfig == null
                ? null : config.modelConfig.getMaxOutputTokens();
        int effectiveOutputLimit = ChatOutputTokenPolicy.resolve(requested, configuredModelCap, maxOutputTokens);
        if (requested == null || requested <= 0 || requested != effectiveOutputLimit) {
            log.debug("输出Token上限已规范化: userId={}, requested={}, effective={}, modelCap={}, packageCap={}",
                    userId, requested, effectiveOutputLimit, configuredModelCap, maxOutputTokens);
        }
        request.setMaxTokens(effectiveOutputLimit);
        log.debug("Token限制检查通过: userId={}, inputLimit={}, outputLimit={}",
                userId, maxInputTokens, effectiveOutputLimit);
    }

    /**
     * 获取或创建会话
     */
    private Long getOrCreateConversation(Long userId, ChatRequest request) {
        if (request.getConversationId() != null) {
            // 再次校验，防止模型调用期间会话被删除后仍写入消息或结算。
            validateConversationOwnership(userId, request);
            return request.getConversationId();
        }

        // 否则创建新会话
        com.ai.gateway.entity.Conversation conversation = new com.ai.gateway.entity.Conversation();
        conversation.setUserId(userId);

        // 使用第一条用户消息作为标题（截取前20个字符，避免emoji等多字节字符截断）
        String firstUserMessage = request.getMessages().stream()
                .filter(msg -> "user".equals(msg.getRole()))
                .findFirst()
                .map(msg -> msg.getTextContent())
                .orElse("新对话");

        // 安全截取：按codepoint而不是char，避免多字节字符截断
        int titleLength = Math.min(firstUserMessage.length(), 20);
        String title = firstUserMessage.length() > 20
                ? firstUserMessage.substring(0, firstUserMessage.offsetByCodePoints(0, titleLength)) + "..."
                : firstUserMessage;

        conversation.setTitle(title);
        conversation.setModel(request.getModel());
        conversation.setMessageCount(0);
        conversation.setLastMessageTime(java.time.LocalDateTime.now());

        conversationMapper.insert(conversation);

        log.info("创建新会话: userId={}, conversationId={}, title={}", userId, conversation.getId(), title);

        return conversation.getId();
    }

    private void validateConversationOwnership(Long userId, ChatRequest request) {
        Long conversationId = request.getConversationId();
        if (conversationId == null) return;

        com.ai.gateway.entity.Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null
                || !Objects.equals(conversation.getUserId(), userId)
                || !Integer.valueOf(0).equals(conversation.getIsDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "会话不存在或无权访问");
        }
    }

    /**
     * 保存对话消息到数据库
     */
    private void saveChatMessages(String requestId, Long conversationId, Long userId, ChatRequest request,
                                  String aiResponse, String model, int totalTokens, long startTime) {
        ChatRequest.ChatMessage latestUserMessage = request.getMessages().stream()
                .filter(message -> "user".equals(message.getRole()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("聊天请求缺少用户消息，无法保存对话"));
        String userContent = latestUserMessage.getTextContent();
        if (userContent == null || userContent.isBlank()) {
            try {
                userContent = objectMapper.writeValueAsString(latestUserMessage.getContent());
            } catch (Exception e) {
                throw new IllegalStateException("无法序列化用户消息，聊天记录未保存", e);
            }
        }

        String fileUrlsJson = null;
        String metadataJson = null;
        try {
            if (request.getFileUrls() != null && !request.getFileUrls().isEmpty()) {
                fileUrlsJson = objectMapper.writeValueAsString(request.getFileUrls());
            }
            List<Map<String, Object>> sourceFiles = request.getFiles();
            if (sourceFiles != null && !sourceFiles.isEmpty()) {
                long expiresAt = System.currentTimeMillis() + 15L * 24 * 60 * 60 * 1000;
                List<Map<String, Object>> copiedFiles = new ArrayList<>(sourceFiles.size());
                for (Map<String, Object> source : sourceFiles) {
                    Map<String, Object> copied = new LinkedHashMap<>(source);
                    copied.putIfAbsent("expireTime", expiresAt);
                    copiedFiles.add(copied);
                }
                metadataJson = objectMapper.writeValueAsString(Map.of("files", copiedFiles));
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法构建聊天文件元数据，聊天记录未保存", e);
        }

        double thinkingTime = Math.round((System.currentTimeMillis() - startTime) / 100.0) / 10.0;
        chatMessageService.saveConversationTurn(requestId, conversationId, userId, userContent, aiResponse,
                model, totalTokens, thinkingTime, fileUrlsJson, metadataJson);
    }

    /**
     * 校验用户是否有权限使用指定模型
     * 根据用户当前套餐的 modelAccessLevel 与模型所需的 accessLevel 进行比较
     */
    private void validateModelAccess(Long userId, PlatformModelConfig modelConfig) {
        if (modelConfig == null) return;

        // 获取模型所需的权限等级
        Integer requiredLevel = modelConfig.getAccessLevel();
        if (requiredLevel == null || requiredLevel == 0) return; // level 0 所有用户可用
        if (requiredLevel < 0 || requiredLevel > 3) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "模型访问等级配置无效，请联系管理员");
        }

        try {
            // 获取用户当前有效订阅
            UserSubscription subscription = userSubscriptionService.getCurrentSubscription(userId);
            int userLevel = 0;

            if (subscription != null) {
                PackageTemplate pkg = packageTemplateMapper.selectById(subscription.getPackageId());
                if (pkg != null && pkg.getModelAccessLevel() != null) {
                    userLevel = pkg.getModelAccessLevel();
                }
            }

            if (userLevel < requiredLevel) {
                // 构建升级提示
                String levelNames[] = {"", "白银", "黄金", "钻石"};
                String requiredName = requiredLevel <= 3 ? levelNames[requiredLevel] : "更高";
                throw new BusinessException(
                        "当前会员等级无法使用模型 " + modelConfig.getDisplayName() +
                                "，请升级至" + requiredName + "会员。"
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("模型权限校验异常: userId={}", userId, e);
            throw new BusinessException(503, "模型权限校验暂时不可用，请稍后重试");
        }
    }
}
