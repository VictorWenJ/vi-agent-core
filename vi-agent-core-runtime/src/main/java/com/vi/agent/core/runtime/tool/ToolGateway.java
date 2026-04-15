package com.vi.agent.core.runtime.tool;

import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;

/**
 * 工具网关统一入口。
 */
public interface ToolGateway {

    /**
     * 路由并执行工具调用。
     *
     * @param toolCall 工具调用请求
     * @return 工具执行结果
     */
    ToolResult route(ToolCall toolCall);
}
