package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * Deprecated compatibility message for tool output.
 */
@Deprecated
public final class ToolExecutionMessage extends AbstractMessage {

    private final String toolCallId;

    private final String toolName;

    private final String toolOutput;

    private ToolExecutionMessage(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String toolOutput,
        Instant createdAt
    ) {
        super(messageId, turnId, MessageRole.TOOL, MessageType.TOOL_RESULT, sequenceNo, toolOutput, createdAt);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolOutput = toolOutput;
    }

    public static ToolExecutionMessage create(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String toolOutput
    ) {
        return new ToolExecutionMessage(messageId, turnId, sequenceNo, toolCallId, toolName, toolOutput, Instant.now());
    }

    public static ToolExecutionMessage restore(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String toolOutput,
        Instant createdAt
    ) {
        return new ToolExecutionMessage(messageId, turnId, sequenceNo, toolCallId, toolName, toolOutput, createdAt);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolOutput() {
        return toolOutput;
    }
}
