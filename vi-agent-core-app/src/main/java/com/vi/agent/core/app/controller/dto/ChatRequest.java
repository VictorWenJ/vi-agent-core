package com.vi.agent.core.app.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 聊天请求 DTO。
 */
@Getter
@Setter
public class ChatRequest {

    /** 会话 ID。 */
    @NotBlank
    private String sessionId;

    /** 用户输入内容。 */
    @NotBlank
    private String message;
}
