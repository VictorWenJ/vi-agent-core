package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool call record status enum.
 */
@Getter
@AllArgsConstructor
public enum ToolCallStatus {

    CREATED("created", "已创建"),

    DISPATCHED("dispatched", "已派发"),

    RUNNING("running", "执行中"),

    SUCCEEDED("succeeded", "执行成功"),

    FAILED("failed", "执行失败"),

    CANCELLED("cancelled", "已取消");

    private final String value;

    private final String desc;
}
