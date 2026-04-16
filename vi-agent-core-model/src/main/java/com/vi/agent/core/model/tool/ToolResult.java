package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工具执行结果。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 工具调用 ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 当前轮次 ID。 */
    private String turnId;

    /** 是否执行成功。 */
    private boolean success;

    /** 工具输出内容。 */
    private String output;

    /** 错误信息。 */
    private String errorMessage;
}
