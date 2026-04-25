package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Evidence 目标类型。
 */
@Getter
@AllArgsConstructor
public enum EvidenceTargetType {

    SESSION_STATE_FIELD("session_state_field", "会话状态字段"),

    SUMMARY_SEGMENT("summary_segment", "会话摘要段落"),

    CONTEXT_BLOCK("context_block", "上下文块"),

    TOOL_OUTCOME_DIGEST("tool_outcome_digest", "工具结果摘要"),

    OPEN_LOOP("open_loop", "未闭环事项");

    /** 稳定外部值。 */
    private final String value;

    /** 中文说明。 */
    private final String desc;
}
