package com.vi.agent.core.infra.observability;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 运行时追踪上下文。
 */
@Getter
@RequiredArgsConstructor
public class TraceContext {

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 运行 ID。 */
    private final String runId;

    /** 会话 ID。 */
    private final String sessionId;
}

