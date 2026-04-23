package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Actor type for run events.
 */
@Getter
@AllArgsConstructor
public enum RunEventActorType {

    USER("user", "用户"),

    MODEL("model", "模型"),

    TOOL("tool", "工具"),

    AGENT("agent", "代理"),

    SYSTEM("system", "系统");

    private final String value;

    private final String desc;
}
