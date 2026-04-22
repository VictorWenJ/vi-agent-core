package com.vi.agent.core.model.message;

import com.vi.agent.core.model.tool.ToolCallStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 助手消息上的工具调用子结构。
 */
@Getter
@Builder
public class AssistantToolCall {

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String assistantMessageId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final String toolName;

    private final String argumentsJson;

    private final Integer callIndex;

    private final ToolCallStatus status;

    private final Instant createdAt;
}
