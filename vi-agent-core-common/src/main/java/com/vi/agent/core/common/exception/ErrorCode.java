package com.vi.agent.core.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Unified error codes.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    RUNTIME_EXECUTION_FAILED("RUNTIME-0001", "Runtime execution failed"),

    RUNTIME_MAX_ITERATIONS_EXCEEDED("RUNTIME-0002", "Max iterations exceeded"),

    TOOL_NOT_FOUND("TOOL-0001", "Tool not found"),

    TOOL_EXECUTION_FAILED("TOOL-0002", "Tool execution failed"),

    TOOL_NOT_REGISTERED("TOOL-0003", "Tool not registered"),

    PROVIDER_CALL_FAILED("PROVIDER-0001", "Model provider call failed"),

    PROVIDER_CONFIG_INVALID_FAILED("PROVIDER-0002", "Model provider configuration invalid"),

    TRANSCRIPT_STORE_FAILED("PERSIST-0001", "Transcript persistence failed"),

    JSON_SERIALIZATION_FAILED("COMMON-0002", "JSON serialization/deserialization failed"),

    INVALID_ARGUMENT("COMMON-0001", "Invalid argument"),

    CONVERSATION_NOT_FOUND("SESSION-0001", "Conversation not found"),

    SESSION_NOT_FOUND("SESSION-0002", "Session not found"),

    SESSION_MODE_INVALID("SESSION-0003", "Session mode is invalid"),

    SESSION_CONVERSATION_MISMATCH("SESSION-0004", "Session does not belong to conversation"),

    SESSION_CONCURRENT_REQUEST("SESSION-0005", "Concurrent request in same session is rejected");

    private final String code;

    private final String message;
}
