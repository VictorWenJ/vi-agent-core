package com.vi.agent.core.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息角色枚举。
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    /** 终端用户输入消息。 */
    USER("user", "终端用户输入消息"),

    /** 模型生成的助手输出消息。 */
    ASSISTANT("assistant", "模型生成的助手输出消息"),

    /** 工具侧输出消息。 */
    TOOL("tool", "工具侧输出消息"),

    /** 系统指令消息。 */
    SYSTEM("system", "系统指令消息"),

    /** 记忆压缩摘要消息；对外协议按 system 角色发送。 */
    SUMMARY("system", "用于记忆压缩的摘要消息");

    /** 对外模型 API 使用的角色值。 */
    private final String apiValue;

    /** 角色描述。 */
    private final String desc;
}
