package com.vi.agent.core.app.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求 DTO。
 */
public class ChatRequest {

    /** 会话 ID。 */
    @NotBlank
    private String sessionId;

    /** 用户输入内容。 */
    @NotBlank
    private String message;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
