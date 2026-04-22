package com.vi.agent.core.runtime.event;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import lombok.Builder;
import lombok.Getter;

/**
 * Runtime 事件对象。
 */
@Getter
@Builder
public class RuntimeEvent {

    private RuntimeEventType eventType;

    private RunStatus runStatus;

    private String requestId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String runId;

    private String messageId;

    private String delta;

    private String content;

    private FinishReason finishReason;

    private UsageInfo usage;

    private AssistantToolCall toolCall;

    private ToolExecution toolResult;

    private String errorCode;

    private String errorMessage;

    private String errorType;

    private boolean retryable;
}
