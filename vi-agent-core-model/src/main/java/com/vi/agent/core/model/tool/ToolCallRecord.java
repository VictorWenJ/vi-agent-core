package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tool call fact record.
 */
@Getter
@Builder
public class ToolCallRecord {

    private final String toolCallId;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String messageId;

    private final String toolName;

    private final String argumentsJson;

    private final int sequenceNo;

    private final String status;

    private final Instant createdAt;
}
