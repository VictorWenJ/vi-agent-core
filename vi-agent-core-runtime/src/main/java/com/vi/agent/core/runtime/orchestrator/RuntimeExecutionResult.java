package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.model.message.AssistantMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Runtime 执行结果。
 */
@Getter
@RequiredArgsConstructor
public class RuntimeExecutionResult {

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 运行 ID。 */
    private final String runId;

    /** 会话 ID。 */
    private final String sessionId;

    /** 助手输出消息。 */
    private final AssistantMessage assistantMessage;
}

