package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

/**
 * Tool execution input.
 */
@Getter
@Builder
public class ToolCall {

    private final String toolCallId;

    private final String toolName;

    private final String argumentsJson;

    private final String turnId;
}
