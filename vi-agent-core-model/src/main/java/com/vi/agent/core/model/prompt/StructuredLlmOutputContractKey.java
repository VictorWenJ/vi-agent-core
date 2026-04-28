package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 结构化 LLM 输出契约 key。
 */
@Getter
@AllArgsConstructor
public enum StructuredLlmOutputContractKey {

    /** 状态增量结构化输出契约。 */
    STATE_DELTA_OUTPUT("state_delta_output", "状态增量结构化输出契约"),

    /** 会话摘要结构化输出契约。 */
    CONVERSATION_SUMMARY_OUTPUT("conversation_summary_output", "会话摘要结构化输出契约");

    /** contract 和审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}
