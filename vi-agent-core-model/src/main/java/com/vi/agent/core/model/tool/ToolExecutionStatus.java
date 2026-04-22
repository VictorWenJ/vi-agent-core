package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具执行状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ToolExecutionStatus {

    /** 执行成功。 */
    SUCCESS("success", "执行成功"),

    /** 执行失败。 */
    FAILED("failed", "执行失败");

    private final String value;

    private final String desc;
}
