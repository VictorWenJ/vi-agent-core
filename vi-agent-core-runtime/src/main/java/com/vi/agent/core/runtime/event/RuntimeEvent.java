package com.vi.agent.core.runtime.event;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import lombok.Builder;
import lombok.Getter;

/**
 * Runtime event object.
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

    private ToolCallRecord toolCall;

    private ToolResultRecord toolResult;

    private String errorCode;

    private String errorMessage;

    private String errorType;

    private boolean retryable;
}
