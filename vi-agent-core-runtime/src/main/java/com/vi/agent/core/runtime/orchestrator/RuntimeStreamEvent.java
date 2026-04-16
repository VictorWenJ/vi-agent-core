package com.vi.agent.core.runtime.orchestrator;

import lombok.Builder;
import lombok.Getter;

/**
 * Runtime 内部流式事件。
 */
@Getter
@Builder
public class RuntimeStreamEvent {

    /** 事件类型。 */
    private RuntimeStreamEventType type;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 会话 ID。 */
    private String sessionId;

    /** 会话链路 ID。 */
    private String conversationId;

    /** 当前轮次 ID。 */
    private String turnId;

    /** 事件内容。 */
    private String content;

    /** 是否完成。 */
    private boolean done;
}
