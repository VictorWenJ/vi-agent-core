package com.vi.agent.core.runtime.memory.extract;

/**
 * Extracts a StateDelta from a completed turn transcript.
 */
public interface StateDeltaExtractor {

    StateDeltaExtractionResult extract(StateDeltaExtractionCommand command);
}
