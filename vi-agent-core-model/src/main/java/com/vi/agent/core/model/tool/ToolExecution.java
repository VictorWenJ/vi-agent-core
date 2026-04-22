package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 工具执行事实记录。
 */
@Getter
@Builder
public class ToolExecution {

    private final String toolExecutionId;

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolResultMessageId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final String toolName;

    private final String argumentsJson;

    private final String outputText;

    private final String outputJson;

    private final ToolExecutionStatus status;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private final Instant startedAt;

    private final Instant completedAt;

    private final Instant createdAt;
}
