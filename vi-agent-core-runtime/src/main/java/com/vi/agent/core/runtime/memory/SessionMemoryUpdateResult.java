package com.vi.agent.core.runtime.memory;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of post-turn session memory update.
 */
@Getter
@Builder(toBuilder = true)
public class SessionMemoryUpdateResult {

    private final boolean success;

    private final boolean degraded;

    private final boolean skipped;

    private final String stateTaskId;

    private final String summaryTaskId;

    private final Long newStateVersion;

    private final Long newSummaryVersion;

    private final String failureReason;
}
