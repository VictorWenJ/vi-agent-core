package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工具调用请求。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 工具参数 JSON。 */
    private String argumentsJson;

    /** 当前轮次 ID。 */
    private String turnId;
}
