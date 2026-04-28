package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统级 prompt 资源目录 key。
 */
@Getter
@AllArgsConstructor
public enum SystemPromptKey {

    /** 主聊天运行指令渲染。 */
    RUNTIME_INSTRUCTION_RENDER("runtime_instruction_render", "主聊天运行指令渲染"),

    /** 会话状态上下文渲染。 */
    SESSION_STATE_RENDER("session_state_render", "会话状态上下文渲染"),

    /** 会话摘要上下文渲染。 */
    CONVERSATION_SUMMARY_RENDER("conversation_summary_render", "会话摘要上下文渲染"),

    /** 状态增量抽取。 */
    STATE_DELTA_EXTRACT("state_delta_extract", "状态增量抽取"),

    /** 会话摘要抽取。 */
    CONVERSATION_SUMMARY_EXTRACT("conversation_summary_extract", "会话摘要抽取");

    /** manifest、资源目录和审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}
