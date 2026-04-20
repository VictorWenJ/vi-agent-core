package com.vi.agent.core.model.llm;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Provider neutral model response.
 */
@Getter
@Builder
public class ModelResponse {

    private final String content;

    private final List<ModelToolCall> toolCalls;

    private final FinishReason finishReason;

    private final UsageInfo usage;

    private final String provider;

    private final String model;
}
