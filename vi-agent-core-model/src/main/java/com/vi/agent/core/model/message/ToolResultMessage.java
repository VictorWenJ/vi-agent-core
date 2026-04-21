package com.vi.agent.core.model.message;

import lombok.Getter;

import java.time.Instant;

/**
 * Tool result message fact.
 */
@Getter
public final class ToolResultMessage extends AbstractMessage {

    private final String toolCallId;

    private final String toolName;

    private final boolean success;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private ToolResultMessage(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        boolean success,
        String content,
        String errorCode,
        String errorMessage,
        Long durationMs,
        Instant createdAt
    ) {
        super(messageId, turnId, MessageRole.TOOL, MessageType.TOOL_RESULT, sequenceNo, content, createdAt);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public static ToolResultMessage create(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        boolean success,
        String content,
        String errorCode,
        String errorMessage,
        Long durationMs
    ) {
        return new ToolResultMessage(
            messageId,
            turnId,
            sequenceNo,
            toolCallId,
            toolName,
            success,
            content,
            errorCode,
            errorMessage,
            durationMs,
            Instant.now()
        );
    }

    public static ToolResultMessage restore(
        String messageId,
        String turnId,
        long sequenceNo,
        String toolCallId,
        String toolName,
        boolean success,
        String content,
        String errorCode,
        String errorMessage,
        Long durationMs,
        Instant createdAt
    ) {
        return new ToolResultMessage(
            messageId,
            turnId,
            sequenceNo,
            toolCallId,
            toolName,
            success,
            content,
            errorCode,
            errorMessage,
            durationMs,
            createdAt
        );
    }

}
