package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Runtime event type enum.
 */
@Getter
@AllArgsConstructor
public enum RunEventType {

    RUN_STARTED("run_started", "run started"),

    MESSAGE_STARTED("message_started", "message started"),

    MESSAGE_DELTA("message_delta", "message delta"),

    TOOL_CALL_CREATED("tool_call_created", "tool call created"),

    TOOL_DISPATCHED("tool_dispatched", "tool dispatched"),

    TOOL_STARTED("tool_started", "tool started"),

    TOOL_COMPLETED("tool_completed", "tool completed"),

    TOOL_FAILED("tool_failed", "tool failed"),

    TOOL_CANCELLED("tool_cancelled", "tool cancelled"),

    MESSAGE_COMPLETED("message_completed", "message completed"),

    RUN_COMPLETED("run_completed", "run completed"),

    RUN_FAILED("run_failed", "run failed");

    private final String value;

    private final String desc;
}
