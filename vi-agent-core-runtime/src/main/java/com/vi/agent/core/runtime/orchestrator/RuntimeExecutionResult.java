package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.model.message.AssistantMessage;

/**
 * Runtime 执行结果。
 */
public class RuntimeExecutionResult {

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 运行 ID。 */
    private final String runId;

    /** 会话 ID。 */
    private final String sessionId;

    /** 助手输出消息。 */
    private final AssistantMessage assistantMessage;

    public RuntimeExecutionResult(String traceId, String runId, String sessionId, AssistantMessage assistantMessage) {
        this.traceId = traceId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.assistantMessage = assistantMessage;
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

    public AssistantMessage getAssistantMessage() {
        return assistantMessage;
    }
}
