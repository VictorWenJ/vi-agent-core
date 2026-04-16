package com.vi.agent.core.app.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天响应 DTO。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 会话链路 ID。 */
    private String conversationId;

    /** 当前轮次 ID。 */
    private String turnId;

    /** 助手输出内容。 */
    private String content;
}
