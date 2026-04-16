package com.vi.agent.core.runtime.orchestrator;

/**
 * Runtime 流式事件类型。
 */
public enum RuntimeStreamEventType {

    /** 运行开始。 */
    START,

    /** 迭代开始。 */
    ITERATION,

    /** 模型分片输出。 */
    TOKEN,

    /** 工具调用。 */
    TOOL_CALL,

    /** 工具结果。 */
    TOOL_RESULT,

    /** 运行完成。 */
    COMPLETE,

    /** 运行异常。 */
    ERROR
}
