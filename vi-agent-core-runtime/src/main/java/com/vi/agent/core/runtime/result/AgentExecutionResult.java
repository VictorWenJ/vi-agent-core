package com.vi.agent.core.runtime.result;

import com.vi.agent.core.model.message.AssistantMessage;
import lombok.Builder;
import lombok.Getter;

/**
 * Runtime 执行结果。
 */
@Getter
@Builder
public class AgentExecutionResult {

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

    /** 助手输出消息。 */
    private AssistantMessage assistantMessage;
}
