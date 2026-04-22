package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行状态枚举。
 */
@Getter
@AllArgsConstructor
public enum AgentRunState {

    /** 已启动。 */
    STARTED("started", "已启动"),

    /** 已完成。 */
    COMPLETED("completed", "已完成"),

    /** 已失败。 */
    FAILED("failed", "已失败");

    private final String value;

    private final String desc;
}
