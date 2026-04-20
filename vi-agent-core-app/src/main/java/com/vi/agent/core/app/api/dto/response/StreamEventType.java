package com.vi.agent.core.app.api.dto.response;

/**
 * 流式响应事件类型。
 */
public enum StreamEventType {
    /** 运行开始事件。 */
    RUN_STARTED,
    /** 助手消息开始事件。 */
    MESSAGE_STARTED,
    /** 助手消息增量分片事件。 */
    MESSAGE_DELTA,
    /** 工具调用事件。 */
    TOOL_CALL,
    /** 工具执行结果事件。 */
    TOOL_RESULT,
    /** 助手消息完成事件。 */
    MESSAGE_COMPLETED,
    /** 运行成功完成事件。 */
    RUN_COMPLETED,
    /** 运行失败事件。 */
    RUN_FAILED
}
