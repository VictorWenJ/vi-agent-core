package com.vi.agent.core.model.message;

/**
 * Message type.
 */
public enum MessageType {
    USER_INPUT,
    ASSISTANT_OUTPUT,
    TOOL_CALL,
    TOOL_RESULT,
    SYSTEM_MESSAGE,
    SUMMARY_MESSAGE
}
