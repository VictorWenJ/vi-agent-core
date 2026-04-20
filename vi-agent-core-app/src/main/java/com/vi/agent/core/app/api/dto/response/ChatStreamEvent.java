package com.vi.agent.core.app.api.dto.response;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.runtime.RunStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * Streaming chat event DTO.
 */
@Getter
@Builder
public class ChatStreamEvent {

    private StreamEventType eventType;

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

    private ToolCallPayload toolCall;

    private ToolResultPayload toolResult;

    private ErrorPayload error;
}
