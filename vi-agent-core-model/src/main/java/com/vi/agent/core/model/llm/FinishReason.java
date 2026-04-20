package com.vi.agent.core.model.llm;

/**
 * Completion finish reason.
 */
public enum FinishReason {
    STOP,
    TOOL_CALL,
    LENGTH,
    ERROR,
    CANCELLED
}
