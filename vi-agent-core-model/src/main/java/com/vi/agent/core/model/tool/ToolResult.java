package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 是否执行成功。 */
    private boolean success;

    /** 工具输出内容。 */
    private String output;
}

