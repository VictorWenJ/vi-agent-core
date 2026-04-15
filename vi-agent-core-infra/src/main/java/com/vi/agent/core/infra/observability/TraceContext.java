package com.vi.agent.core.infra.observability;

/**
 * 运行时追踪上下文。
 */
public class TraceContext {

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 运行 ID。 */
    private final String runId;

    /** 会话 ID。 */
    private final String sessionId;

    public TraceContext(String traceId, String runId, String sessionId) {
        this.traceId = traceId;
        this.runId = runId;
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
