package com.vi.agent.core.runtime.event;

/**
 * Runtime stream event type.
 */
public enum RuntimeEventType {
    RUN_STARTED,
    MESSAGE_STARTED,
    MESSAGE_DELTA,
    TOOL_CALL,
    TOOL_RESULT,
    MESSAGE_COMPLETED,
    RUN_COMPLETED,
    RUN_FAILED
}
