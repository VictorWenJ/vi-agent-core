package com.vi.agent.core.app.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 流式响应分片 DTO。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 会话链路 ID。 */
    private String conversationId;

    /** 当前轮次 ID。 */
    private String turnId;

    /** 当前分片内容。 */
    private String content;

    /** 是否结束。 */
    private boolean done;
}
