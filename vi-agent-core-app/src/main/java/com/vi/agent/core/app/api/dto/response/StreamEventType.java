package com.vi.agent.core.app.api.dto.response;

/**
 * Stream event type.
 */
public enum StreamEventType {
    RUN_STARTED,
    MESSAGE_STARTED,
    MESSAGE_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    MESSAGE_COMPLETED,
    RUN_COMPLETED,
    RUN_FAILED
}
