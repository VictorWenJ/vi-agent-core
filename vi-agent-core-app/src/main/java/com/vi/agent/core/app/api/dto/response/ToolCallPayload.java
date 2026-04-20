package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Tool call event payload.
 */
@Getter
@Builder
public class ToolCallPayload {

    private String toolCallId;

    private String toolName;

    private String argumentsJson;

    private Integer sequence;

    private Instant createdAt;
}
