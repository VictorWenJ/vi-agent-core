package com.vi.agent.core.model.message;

import com.vi.agent.core.model.tool.ToolExecutionStatus;
import lombok.Getter;

import java.time.Instant;

/**
 * 工具结果消息（provider role=tool）。
 */
@Getter
public final class ToolMessage extends AbstractMessage {

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolName;

    private final ToolExecutionStatus executionStatus;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private final String argumentsJson;

    private ToolMessage(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        String toolCallRecordId,
        String toolCallId,
        String toolName,
        ToolExecutionStatus executionStatus,
        String errorCode,
        String errorMessage,
        Long durationMs,
        String argumentsJson,
        Instant createdAt
    ) {
        super(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            MessageRole.TOOL,
            MessageType.TOOL_RESULT,
            sequenceNo,
            status,
            contentText,
            createdAt
        );
        this.toolCallRecordId = toolCallRecordId;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.executionStatus = executionStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.argumentsJson = argumentsJson;
    }

    public static ToolMessage create(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String contentText,
        String toolCallRecordId,
        String toolCallId,
        String toolName,
        ToolExecutionStatus executionStatus,
        String errorCode,
        String errorMessage,
        Long durationMs,
        String argumentsJson
    ) {
        return new ToolMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            MessageStatus.COMPLETED,
            contentText,
            toolCallRecordId,
            toolCallId,
            toolName,
            executionStatus,
            errorCode,
            errorMessage,
            durationMs,
            argumentsJson,
            Instant.now()
        );
    }

    public static ToolMessage restore(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        MessageStatus status,
        String contentText,
        String toolCallRecordId,
        String toolCallId,
        String toolName,
        ToolExecutionStatus executionStatus,
        String errorCode,
        String errorMessage,
        Long durationMs,
        String argumentsJson,
        Instant createdAt
    ) {
        return new ToolMessage(
            messageId,
            conversationId,
            sessionId,
            turnId,
            runId,
            sequenceNo,
            status,
            contentText,
            toolCallRecordId,
            toolCallId,
            toolName,
            executionStatus,
            errorCode,
            errorMessage,
            durationMs,
            argumentsJson,
            createdAt
        );
    }

}
