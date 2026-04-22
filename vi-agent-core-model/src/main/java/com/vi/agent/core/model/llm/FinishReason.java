package com.vi.agent.core.model.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型完成原因枚举。
 */
@Getter
@AllArgsConstructor
public enum FinishReason {

    /** 正常停止。 */
    STOP("stop", "正常停止"),

    /** 触发工具调用。 */
    TOOL_CALL("tool_call", "触发工具调用"),

    /** 长度截断。 */
    LENGTH("length", "长度截断"),

    /** 运行错误。 */
    ERROR("error", "运行错误"),

    /** 被取消。 */
    CANCELLED("cancelled", "被取消");

    private final String value;

    private final String desc;
}
