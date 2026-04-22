package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Checkpoint 类型枚举。
 */
@Getter
@AllArgsConstructor
public enum CheckpointType {

    /** 对话上下文快照。 */
    CONTEXT_SNAPSHOT("context_snapshot", "对话上下文快照"),

    /** 运行状态快照。 */
    RUN_STATE("run_state", "运行状态快照"),

    /** 子代理状态快照。 */
    SUBAGENT_STATE("subagent_state", "子代理状态快照");

    private final String value;

    private final String desc;
}
