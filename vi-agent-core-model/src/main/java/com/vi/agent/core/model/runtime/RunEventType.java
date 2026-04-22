package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行事件类型枚举。
 */
@Getter
@AllArgsConstructor
public enum RunEventType {

    /** 运行开始。 */
    RUN_STARTED("run_started", "运行开始"),

    /** 消息开始。 */
    MESSAGE_STARTED("message_started", "消息开始"),

    /** 消息增量。 */
    MESSAGE_DELTA("message_delta", "消息增量"),

    /** 工具调用创建。 */
    TOOL_CALL_CREATED("tool_call_created", "工具调用创建"),

    /** 工具执行开始。 */
    TOOL_STARTED("tool_started", "工具执行开始"),

    /** 工具执行完成。 */
    TOOL_COMPLETED("tool_completed", "工具执行完成"),

    /** 工具执行失败。 */
    TOOL_FAILED("tool_failed", "工具执行失败"),

    /** 消息完成。 */
    MESSAGE_COMPLETED("message_completed", "消息完成"),

    /** 运行完成。 */
    RUN_COMPLETED("run_completed", "运行完成"),

    /** 运行失败。 */
    RUN_FAILED("run_failed", "运行失败");

    private final String value;

    private final String desc;
}
