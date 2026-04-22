package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 工具结果事件载荷。
 */
@Getter
@Builder
public class ToolResultPayload {

    private String toolExecutionId;

    private String toolCallRecordId;

    private String toolCallId;

    private String toolResultMessageId;

    private String toolName;

    private String argumentsJson;

    private String outputText;

    private String outputJson;

    private String status;

    private String errorCode;

    private String errorMessage;

    private Long durationMs;

    private Instant startedAt;

    private Instant completedAt;

    private Instant createdAt;
}
