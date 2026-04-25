package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 约束作用域。
 */
@Getter
@AllArgsConstructor
public enum ConstraintScope {

    SESSION("session", "会话级约束");

    private final String value;

    private final String desc;
}
