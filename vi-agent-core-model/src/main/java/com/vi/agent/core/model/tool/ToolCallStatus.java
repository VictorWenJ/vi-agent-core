package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具调用记录状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ToolCallStatus {

    /** 已创建。 */
    CREATED("created", "已创建"),

    /** 已分发执行。 */
    DISPATCHED("dispatched", "已分发执行"),

    /** 已完成。 */
    COMPLETED("completed", "已完成"),

    /** 已失败。 */
    FAILED("failed", "已失败");

    private final String value;

    private final String desc;
}
