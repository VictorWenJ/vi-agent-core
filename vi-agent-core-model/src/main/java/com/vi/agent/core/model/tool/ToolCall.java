package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 工具参数 JSON。 */
    private String argumentsJson;
}

