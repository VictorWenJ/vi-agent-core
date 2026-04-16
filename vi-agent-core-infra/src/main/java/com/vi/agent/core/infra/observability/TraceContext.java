package com.vi.agent.core.infra.observability;

import lombok.Builder;
import lombok.Getter;

/**
 * 运行时追踪上下文。
 */
@Getter
@Builder
public class TraceContext {

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
}
