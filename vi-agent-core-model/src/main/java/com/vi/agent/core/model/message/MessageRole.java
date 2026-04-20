package com.vi.agent.core.model.message;

/**
 * 消息角色枚举。
 */
public enum MessageRole {
    /** 终端用户输入消息。 */
    USER,
    /** 模型生成的助手输出消息。 */
    ASSISTANT,
    /** 工具侧输出消息。 */
    TOOL,
    /** 系统指令消息。 */
    SYSTEM,
    /** 用于记忆压缩的摘要消息。 */
    SUMMARY
}
