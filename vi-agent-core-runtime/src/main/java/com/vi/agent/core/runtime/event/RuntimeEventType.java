package com.vi.agent.core.runtime.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Runtime 事件类型枚举。
 */
@Getter
@AllArgsConstructor
public enum RuntimeEventType {

    /** 运行开始事件。 */
    RUN_STARTED("run_started", "运行开始事件"),

    /** 消息开始事件。 */
    MESSAGE_STARTED("message_started", "消息开始事件"),

    /** 消息增量事件。 */
    MESSAGE_DELTA("message_delta", "消息增量事件"),

    /** 工具调用事件。 */
    TOOL_CALL("tool_call", "工具调用事件"),

    /** 工具结果事件。 */
    TOOL_RESULT("tool_result", "工具结果事件"),

    /** 消息完成事件。 */
    MESSAGE_COMPLETED("message_completed", "消息完成事件"),

    /** 运行完成事件。 */
    RUN_COMPLETED("run_completed", "运行完成事件"),

    /** 运行失败事件。 */
    RUN_FAILED("run_failed", "运行失败事件");

    private final String value;

    private final String desc;
}
