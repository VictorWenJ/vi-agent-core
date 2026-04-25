package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Evidence 目标类型。
 */
@Getter
@AllArgsConstructor
public enum EvidenceTargetType {

    SESSION_STATE("session_state", "会话状态"),

    CONVERSATION_SUMMARY("conversation_summary", "会话摘要"),

    CONTEXT_BLOCK("context_block", "上下文块"),

    TOOL_OUTCOME_DIGEST("tool_outcome_digest", "工具结果摘要"),

    OPEN_LOOP("open_loop", "未闭环事项");

    private final String value;

    private final String desc;
}
