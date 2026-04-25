package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Context rebuild checkpoint 触发点。
 */
@Getter
@AllArgsConstructor
public enum CheckpointTrigger {

    BEFORE_FIRST_MODEL_CALL("before_first_model_call", "首次模型调用前"),

    AFTER_TOOL_RESULT_BEFORE_NEXT_MODEL_CALL("after_tool_result_before_next_model_call", "工具结果后且下次模型调用前"),

    POST_TURN("post_turn", "轮次结束后"),

    INTERNAL_TASK("internal_task", "内部任务上下文构建");

    private final String value;

    private final String desc;
}
