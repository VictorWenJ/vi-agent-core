package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 结构化 LLM 输出最终映射目标。
 */
@Getter
@AllArgsConstructor
public enum StructuredLlmOutputTarget {

    /** 状态增量抽取结果。 */
    STATE_DELTA_EXTRACTION_RESULT(
        "state_delta_extraction_result",
        "状态增量抽取结果"
    ),

    /** 会话摘要抽取结果。 */
    CONVERSATION_SUMMARY_EXTRACTION_RESULT(
        "conversation_summary_extraction_result",
        "会话摘要抽取结果"
    );

    /** contract 中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}
