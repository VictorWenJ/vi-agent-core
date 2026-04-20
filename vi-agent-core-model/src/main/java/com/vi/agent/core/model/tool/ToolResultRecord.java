package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tool result fact record.
 */
@Getter
@Builder
public class ToolResultRecord {

    private final String toolCallId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String messageId;

    private final String toolName;

    private final boolean success;

    private final String outputJson;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;

    private final Instant createdAt;
}
