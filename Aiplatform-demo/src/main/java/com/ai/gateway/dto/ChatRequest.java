package com.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {

    @NotBlank(message = "模型名称不能为空")
    private String model;

    @NotNull(message = "消息列表不能为空")
    @NotEmpty(message = "消息列表不能为空")
    private List<ChatMessage> messages;

    private Boolean stream = false;

    private Integer maxTokens;

    private Double temperature;

    private String mode;

    /**
     * 会话ID（可选，如果不传则自动创建新会话）
     */
    private Long conversationId;

    /**
     * 关联文件URL列表（用户上传的文件）
     */
    private List<String> fileUrls;

    @Data
    public static class ChatMessage {
        private String role;
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
                        if ("image_url".equals(map.get("type"))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
