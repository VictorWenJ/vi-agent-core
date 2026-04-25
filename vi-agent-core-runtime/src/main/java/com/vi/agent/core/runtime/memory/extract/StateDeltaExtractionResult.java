package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.memory.StateDelta;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Result of a StateDelta extraction attempt.
 */
@Getter
@Builder(toBuilder = true)
public class StateDeltaExtractionResult {

    private final boolean success;

    private final boolean degraded;

    private final StateDelta stateDelta;

    private final String rawOutput;

    private final String failureReason;

    @Singular("sourceCandidateId")
    private final List<String> sourceCandidateIds;
}
