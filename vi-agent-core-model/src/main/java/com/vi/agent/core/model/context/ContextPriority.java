package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上下文块优先级。
 */
@Getter
@AllArgsConstructor
public enum ContextPriority {

    MANDATORY("mandatory", "必须保留"),

    HIGH("high", "高优先级"),

    MEDIUM("medium", "中优先级"),

    LOW("low", "低优先级");

    private final String value;

    private final String desc;
}
