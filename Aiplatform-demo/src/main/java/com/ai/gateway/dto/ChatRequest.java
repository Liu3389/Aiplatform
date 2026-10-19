package com.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "对话请求")
public class ChatRequest {

    @Schema(description = "模型名称", example = "gpt-4", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模型名称不能为空")
    private String model;

    @Schema(description = "消息列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "消息列表不能为空")
    @NotEmpty(message = "消息列表不能为空")
    private List<ChatMessage> messages;

    @Schema(description = "是否流式返回(SSE)", example = "false")
    private Boolean stream = false;

    /** 幂等键：同一客户端重试请求时复用，服务端只保存其用户级摘要。 */
    @Schema(description = "请求幂等键；网络重试时应复用同一个值", example = "c5ff31a6-5e2a-48a8-b0f7-7aef64d77c5d")
    private String idempotencyKey;

    @Schema(description = "最大生成token数", example = "2048")
    private Integer maxTokens;

    @Schema(description = "温度参数(0-2)", example = "0.7")
    private Double temperature;

    @Schema(description = "兼容字段；平台对话统一使用点数结算，服务端忽略 free 等旧模式")
    private String mode;

    /**
     * 会话ID（可选，如果不传则自动创建新会话）
     */
    @Schema(description = "会话ID(可选,不传则自动创建新会话)")
    private Long conversationId;

    /**
     * 关联文件URL列表（用户上传的文件）
     */
    @Schema(description = "关联文件URL列表(用户上传的文件)")
    private List<String> fileUrls;

    /**
     * 搜索引擎: searxng / jina / auto
     * 为空时使用全局配置 searxng.engine
     */
    @Schema(description = "搜索引擎: searxng/jina/auto, 为空则使用全局配置")
    private String searchEngine;

    /**
     * 是否禁用联网搜索。
     * 带文件对话时置 true：用户是对上传文档提问，联网搜索会把文档全文误当作搜索词
     * （文档中的"2026""最新"等词会命中时效性关键词触发搜索），搜回无关结果污染回答。
     */
    @Schema(description = "是否禁用联网搜索(带文件对话时应为true)")
    private Boolean disableSearch;

    /**
     * 用户上传的文件元数据列表（用于持久化到消息中，让文件卡片跨会话显示）
     * 每项包含: fileId, fileName, fileSize, fileType, ossUrl, uploadTime
     * 后端保存用户消息时写入 metadata 字段，前端加载消息时解析恢复文件卡片
     */
    @Schema(description = "文件元数据列表(用于持久化文件卡片)")
    private List<Map<String, Object>> files;

    @Data
    @Schema(description = "对话消息")
    public static class ChatMessage {
        @Schema(description = "角色: system/user/assistant", example = "user")
        private String role;
        @Schema(description = "内容(字符串或富文本数组,支持文本与图片)")
        private Object content;

        @JsonIgnore
        public String getTextContent() {
            if (content instanceof String) {
                return (String) content;
            }
            if (content instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object part : (List<?>) content) {
                    if (part instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) part;
                        if ("text".equals(map.get("type")) && map.get("text") instanceof String) {
                            sb.append((String) map.get("text"));
                        }
                    }
                }
                return sb.toString();
            }
            return "";
        }

        @JsonIgnore
        public boolean hasImage() {
            if (content instanceof List) {
                for (Object part : (List<?>) content) {
                    if (part instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) part;
                        if ("image_url".equals(map.get("type")) || "input_image".equals(map.get("type"))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
