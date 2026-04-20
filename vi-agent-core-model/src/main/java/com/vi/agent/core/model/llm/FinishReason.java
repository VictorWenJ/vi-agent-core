package com.vi.agent.core.model.llm;

/**
 * 模型完成原因枚举。
 */
public enum FinishReason {
    /** 正常停止完成。 */
    STOP,
    /** 触发工具调用后结束当前轮模型输出。 */
    TOOL_CALL,
    /** 因长度或 token 上限被截断。 */
    LENGTH,
    /** 模型调用发生错误。 */
    ERROR,
    /** 由调用方或系统取消。 */
    CANCELLED
}
