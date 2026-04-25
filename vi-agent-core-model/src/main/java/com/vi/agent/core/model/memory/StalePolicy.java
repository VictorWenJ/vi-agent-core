package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事实过期策略。
 */
@Getter
@AllArgsConstructor
public enum StalePolicy {

    SESSION("session", "会话内有效");

    private final String value;

    private final String desc;
}
