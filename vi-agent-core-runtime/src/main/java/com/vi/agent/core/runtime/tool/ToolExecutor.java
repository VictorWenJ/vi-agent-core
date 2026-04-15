package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;

/**
 * 工具执行器。
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具调用。
     *
     * @param toolCall 工具调用请求
     * @return 工具执行结果
     */
    ToolResult execute(ToolCall toolCall);
}
