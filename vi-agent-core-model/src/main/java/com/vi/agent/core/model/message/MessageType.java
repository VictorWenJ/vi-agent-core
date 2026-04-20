package com.vi.agent.core.model.message;

/**
 * 消息类型枚举。
 */
public enum MessageType {
    /** 用户输入消息类型。 */
    USER_INPUT,
    /** 助手输出消息类型。 */
    ASSISTANT_OUTPUT,
    /** 助手工具调用消息类型。 */
    TOOL_CALL,
    /** 工具执行结果消息类型。 */
    TOOL_RESULT,
    /** 系统指令消息类型。 */
    SYSTEM_MESSAGE,
    /** 记忆管理用摘要消息类型。 */
    SUMMARY_MESSAGE
}
