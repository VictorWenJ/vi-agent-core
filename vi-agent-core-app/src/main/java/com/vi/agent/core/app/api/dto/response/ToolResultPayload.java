package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tool result event payload.
 */
@Getter
@Builder
public class ToolResultPayload {

    private String toolCallId;

    private String toolName;

    private boolean success;

    private String outputJson;

    private String errorCode;

    private String errorMessage;

    private Long durationMs;

    private Instant createdAt;
}
