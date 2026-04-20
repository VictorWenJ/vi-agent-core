package com.vi.agent.core.model.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * Tool call in model response.
 */
@Getter
@Builder
public class ModelToolCall {

    private final String toolCallId;

    private final String toolName;

    private final String argumentsJson;
}
