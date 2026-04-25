package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Evidence 来源类型。
 */
@Getter
@AllArgsConstructor
public enum EvidenceSourceType {

    USER_MESSAGE("user_message", "用户消息"),

    ASSISTANT_MESSAGE("assistant_message", "助手消息"),

    TOOL_RESULT("tool_result", "工具结果"),

    SYSTEM_RULE("system_rule", "系统规则"),

    INTERNAL_TASK("internal_task", "内部任务");

    private final String value;

    private final String desc;
}
