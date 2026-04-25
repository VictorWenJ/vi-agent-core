package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 运行模式。
 */
@Getter
@AllArgsConstructor
public enum AgentMode {

    GENERAL("general", "通用模式"),

    ADVISOR("advisor", "顾问型模式"),

    WORKBENCH("workbench", "工具工作台模式");

    private final String value;

    private final String desc;
}
