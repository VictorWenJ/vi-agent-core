package com.vi.agent.core.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息角色枚举。
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    /** 用户消息角色。 */
    USER("user", "用户消息角色"),

    /** 助手消息角色。 */
    ASSISTANT("assistant", "助手消息角色"),

    /** 工具消息角色。 */
    TOOL("tool", "工具消息角色"),

    /** 系统消息角色。 */
    SYSTEM("system", "系统消息角色"),

    /** 摘要消息角色。 */
    SUMMARY("summary", "摘要消息角色");

    private final String value;

    private final String desc;
}
