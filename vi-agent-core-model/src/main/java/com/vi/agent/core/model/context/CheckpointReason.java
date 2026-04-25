package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Context rebuild checkpoint 原因。
 */
@Getter
@AllArgsConstructor
public enum CheckpointReason {

    INITIAL_BUILD("initial_build", "首次构建上下文"),

    TOOL_LOOP_EXPANDING("tool_loop_expanding", "工具循环导致上下文膨胀"),

    INPUT_BUDGET_NEAR_LIMIT("input_budget_near_limit", "输入预算接近上限"),

    FORCED_COMPACTION("forced_compaction", "策略强制压缩"),

    POST_TURN_REFRESH("post_turn_refresh", "轮次结束刷新");

    private final String value;

    private final String desc;
}
