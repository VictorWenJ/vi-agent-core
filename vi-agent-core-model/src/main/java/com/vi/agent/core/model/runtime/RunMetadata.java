package com.vi.agent.core.model.runtime;

import lombok.Builder;
import lombok.Getter;

/**
 * Run metadata ids.
 */
@Getter
@Builder
public class RunMetadata {

    private final String traceId;

    private final String runId;

    private final String turnId;

    private final String userMessageId;

    private final String assistantMessageId;
}
