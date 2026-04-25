package com.vi.agent.core.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Runtime event type enum.
 */
@Getter
@AllArgsConstructor
public enum RunEventType {

    RUN_STARTED("run_started", "运行开始"),

    MESSAGE_STARTED("message_started", "消息开始"),

    MESSAGE_DELTA("message_delta", "消息增量"),

    TOOL_CALL_CREATED("tool_call_created", "工具调用已创建"),

    TOOL_DISPATCHED("tool_dispatched", "工具已派发"),

    TOOL_STARTED("tool_started", "工具开始执行"),

    TOOL_COMPLETED("tool_completed", "工具执行完成"),

    TOOL_FAILED("tool_failed", "工具执行失败"),

    TOOL_CANCELLED("tool_cancelled", "工具调用已取消"),

    MESSAGE_COMPLETED("message_completed", "消息完成"),

    RUN_COMPLETED("run_completed", "运行完成"),

    RUN_FAILED("run_failed", "运行失败"),

    INTERNAL_TASK_STARTED("internal_task_started", "内部任务开始执行"),

    INTERNAL_TASK_SUCCEEDED("internal_task_succeeded", "内部任务执行成功"),

    INTERNAL_TASK_FAILED("internal_task_failed", "内部任务执行失败"),

    INTERNAL_TASK_DEGRADED("internal_task_degraded", "内部任务降级完成"),

    INTERNAL_TASK_SKIPPED("internal_task_skipped", "内部任务策略跳过");

    private final String value;

    private final String desc;
}
