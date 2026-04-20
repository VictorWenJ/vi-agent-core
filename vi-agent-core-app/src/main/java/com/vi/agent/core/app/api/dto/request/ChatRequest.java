package com.vi.agent.core.app.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * 聊天请求 DTO。
 */
@Getter
@Builder
public class ChatRequest {

    /**
     * 产品层对话线程 ID。
     * 新建会话时可为空，由后端创建并返回。
     */
    private String conversationId;

    /**
     * runtime / memory 实例 ID。
     * 普通继续聊天时可为空，由后端按 conversationId 查找当前 active session；
     * 显式切换/重建 session 时可传入。
     */
    private String sessionId;

    /**
     * 客户端请求 ID。
     * 由前端生成，用于前后端请求关联、占位消息回填、去重、流式关联。
     */
    @NotBlank(message = "requestId must not be blank")
    private String requestId;

    /**
     * 当前用户输入。
     */
    @NotBlank(message = "message must not be blank")
    private String message;
}