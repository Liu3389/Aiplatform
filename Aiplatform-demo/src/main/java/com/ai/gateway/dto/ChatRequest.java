package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

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

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
