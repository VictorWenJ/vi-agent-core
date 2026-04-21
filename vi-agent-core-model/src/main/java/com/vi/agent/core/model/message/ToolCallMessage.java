package com.vi.agent.core.model.message;

import lombok.Getter;

import java.time.Instant;

/**
 * Tool call message fact.
 */
@Getter
public final class ToolCallMessage extends AbstractMessage {

    private final String toolCallId;

    private final String toolName;

    private final String argumentsJson;

    private ToolCallMessage(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String argumentsJson,
        Instant createdAt
    ) {
        super(messageId, turnId, MessageRole.TOOL, MessageType.TOOL_CALL, sequenceNo, argumentsJson, createdAt);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }

    public static ToolCallMessage create(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String argumentsJson
    ) {
        return new ToolCallMessage(messageId, turnId, sequenceNo, toolCallId, toolName, argumentsJson, Instant.now());
    }

    public static ToolCallMessage restore(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        String argumentsJson,
        Instant createdAt
    ) {
        return new ToolCallMessage(messageId, turnId, sequenceNo, toolCallId, toolName, argumentsJson, createdAt);
    }

}
