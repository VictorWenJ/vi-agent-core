package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool execution status enum.
 */
@Getter
@AllArgsConstructor
public enum ToolExecutionStatus {

    RUNNING("running", "running"),

    SUCCEEDED("succeeded", "succeeded"),

    FAILED("failed", "failed");

    private final String value;

    private final String desc;
}
