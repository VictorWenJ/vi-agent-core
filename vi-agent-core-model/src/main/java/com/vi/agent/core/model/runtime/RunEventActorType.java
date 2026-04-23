package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Actor type for run events.
 */
@Getter
@AllArgsConstructor
public enum RunEventActorType {

    ASSISTANT("assistant", "assistant"),

    TOOL("tool", "tool"),

    RUNTIME("runtime", "runtime");

    private final String value;

    private final String desc;
}