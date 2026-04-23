package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool call record status enum.
 */
@Getter
@AllArgsConstructor
public enum ToolCallStatus {

    CREATED("created", "created"),

    DISPATCHED("dispatched", "dispatched"),

    RUNNING("running", "running"),

    SUCCEEDED("succeeded", "succeeded"),

    FAILED("failed", "failed"),

    CANCELLED("cancelled", "cancelled");

    private final String value;

    private final String desc;
}
