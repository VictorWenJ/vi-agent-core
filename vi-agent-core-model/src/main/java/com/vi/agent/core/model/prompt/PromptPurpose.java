package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统级 prompt 用途。
 */
@Getter
@AllArgsConstructor
public enum PromptPurpose {

    /** 主聊天运行指令渲染。 */
    RUNTIME_INSTRUCTION_RENDER("runtime_instruction_render", "主聊天运行指令渲染"),

    /** 会话状态上下文渲染。 */
    SESSION_STATE_RENDER("session_state_render", "会话状态上下文渲染"),

    /** 会话摘要上下文渲染。 */
    CONVERSATION_SUMMARY_RENDER("conversation_summary_render", "会话摘要上下文渲染"),

    /** 状态增量抽取。 */
    STATE_DELTA_EXTRACTION("state_delta_extraction", "状态增量抽取"),

    /** 会话摘要抽取。 */
    CONVERSATION_SUMMARY_EXTRACTION("conversation_summary_extraction", "会话摘要抽取");

    /** manifest 和审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}
