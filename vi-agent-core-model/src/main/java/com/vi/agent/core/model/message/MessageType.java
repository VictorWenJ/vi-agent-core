package com.vi.agent.core.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举。
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    /** 用户输入消息。 */
    USER_INPUT("user_input", "用户输入消息"),

    /** 助手输出消息。 */
    ASSISTANT_OUTPUT("assistant_output", "助手输出消息"),

    /** 工具结果消息。 */
    TOOL_RESULT("tool_result", "工具结果消息"),

    /** 系统提示消息。 */
    SYSTEM_PROMPT("system_prompt", "系统提示消息"),

    /** 摘要上下文消息。 */
    SUMMARY_CONTEXT("summary_context", "摘要上下文消息");

    private final String value;

    private final String desc;
}
